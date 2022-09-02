package service.impl

import cats.Monad
import cats.data.EitherT
import cats.effect.std.Semaphore
import cats.syntax.all._
import domain.queue
import domain.queue._
import domain.user.StudentReadDomain
import error.BotError
import error.impl.queue._
import org.typelevel.log4cats.Logger
import repository.{QueueRepository, StudentRepository}
import service.QueueService

import java.time.LocalDate

class QueueServiceImpl[F[_]: Monad](
  studentRepository:   StudentRepository[F],
  queueRepository:     QueueRepository[F],
  addToQueueSemaphore: Semaphore[F]
)(
  implicit logger: Logger[F]
) extends QueueService[F] {
  override def getQueueSeries(student: StudentReadDomain): F[List[QueueSeries]] = {
    queueRepository.getQueueSeries(student)
  }

  private def getPlaceFromOption(
    student: StudentReadDomain,
    qsId:    Int,
    date:    LocalDate,
    option:  AddToQueueOption,
    place:   Option[Int]
  ): F[Either[BotError, Int]] = {
    for {
      place <- option match {
        case AddToQueueOption.PushFront =>
          val res = for {
            availablePlaces <- EitherT(getAvailablePlaces(student, qsId, date))
          } yield availablePlaces.min
          res.value
        case AddToQueueOption.PushBack =>
          val res = for {
            availablePlaces <- EitherT(getAvailablePlaces(student, qsId, date))
          } yield availablePlaces.max
          res.value
        case AddToQueueOption.TakePlace =>
          val res = for {
            availablePlaces <- EitherT(getAvailablePlaces(student, qsId, date))

            placeE <- EitherT.fromOption(place, InvalidOption: BotError)

            _ <- EitherT.cond(availablePlaces.contains(placeE), (), TakePlaceFailed: BotError)
          } yield placeE
          res.value
        case _ => (InvalidOption: BotError).asLeft[Int].pure[F]
      }
    } yield place
  }

  override def addToQueue(
    student: StudentReadDomain,
    qsId:    Int,
    date:    LocalDate,
    option:  queue.AddToQueueOption,
    place:   Option[Int]
  ): F[Either[BotError, Int]] = {
    val res = for {
      queueOpt <- EitherT.liftF(queueRepository.getQueue(qsId, date))
      queueId <- EitherT.liftF(
        queueOpt.fold(queueRepository.createQueue(qsId, date))(value => value.id.pure)
      )
      records <- EitherT.liftF(queueRepository.getRecords(queueId))
      _       <- EitherT.cond(!records.exists(_.studentId == student.userId), (), StudentAlreadyTakePlaceInQueue: BotError)
      _       <- EitherT.liftF(addToQueueSemaphore.acquire)

      place <- EitherT(getPlaceFromOption(student, qsId, date, option, place)).leftFlatMap(e => {
        for {
          _ <- EitherT.liftF(addToQueueSemaphore.release)

          res <- EitherT.left[Int](e.pure)
        } yield res
      })
      cnt <- EitherT.liftF(queueRepository.takePlace(student.userId, queueId, place))
      _   <- EitherT.liftF(addToQueueSemaphore.release)

      _ <- EitherT.cond(cnt == 1, (), TakePlaceFailed: BotError)
    } yield place
    res.value
  }

  override def getAvailablePlaces(
    student: StudentReadDomain,
    qsId:    Int,
    date:    LocalDate
  ): F[Either[BotError, List[Int]]] = {
    val res = for {
      _        <- EitherT.liftF(logger.debug(s"Params - qsID = ${qsId}, date = ${date}"))
      queueOpt <- EitherT.liftF(queueRepository.getQueue(qsId, date)): EitherT[F, BotError, Option[QueueDbReadDomain]]
      _        <- EitherT.liftF(logger.debug(s"One has got a queue ${queueOpt}"))
      queueId <- EitherT.liftF(
        queueOpt.fold(queueRepository.createQueue(qsId, date))(value => value.id.pure)
      ): EitherT[F, BotError, Int]
      _        <- EitherT.liftF(logger.debug(s"Queue id - ${queueId}"))
      cnt      <- EitherT.liftF(studentRepository.getGroupSize(student)): EitherT[F, BotError, Int]
      _        <- EitherT.liftF(logger.debug(s"Group size - ${cnt}"))
      allPlaces = (1 to cnt).toList
      records  <- EitherT.liftF(queueRepository.getRecords(queueId)):     EitherT[F, BotError, List[QueueRecordReadDomain]]
      _ <- EitherT.liftF(
        logger.debug(s"Got queue record places from DB - ${records.map(_.place.toString).fold("")(_ |+| "\n" |+| _)}")
      ): EitherT[F, BotError, Unit]
    } yield allPlaces.diff(records.map(_.place))

    res.value
  }

  override def getQueue(qsId: Int, date: LocalDate): F[Either[BotError, Queue]] = {
    val res = for {
      queue      <- EitherT.fromOptionF(queueRepository.getQueue(qsId, date), QueueNotFound: BotError)
      records    <- EitherT.liftF(queueRepository.getRecords(queue.id)): EitherT[F, BotError, List[QueueRecordReadDomain]]
      studentIds <- EitherT.fromOption(records.map(_.studentId).toNel, QueueNotFound: BotError)
      students <- EitherT
        .liftF(studentRepository.getStudentsByIds(studentIds)): EitherT[F, BotError, List[
        StudentReadDomain
      ]]
      _ <- EitherT.liftF(
        logger.debug(s"All students in queue - ${students.map(_.toString).fold("")(_ |+| _ |+| "\n")}")
      ): EitherT[F, BotError, Unit]
      queueRecords = records.map(qr => QueueRecord(qr.id, qr.place, students.find(_.userId == qr.studentId).get))
      _ <- EitherT.liftF(
      logger.debug(s"All records with students - ${queueRecords.map(_.toString).fold ("") (_ |+| _ |+| "\n")}")
      ): EitherT[F, BotError, Unit]
    } yield Queue(queue.id, queue.queueSeriesId, queue.date, queueRecords)

    res.value
  }

  override def removeFromQueue(student: StudentReadDomain, qsId: Int, date: LocalDate): F[Either[BotError, Int]] = {
    val res = for {
      queue <- EitherT.fromOptionF(queueRepository.getQueue(qsId, date), QueueNotFound: BotError)
      cnt   <- EitherT.liftF(queueRepository.removeFromQueue(queue.id, student.userId))
      _     <- EitherT.cond(cnt != 0, (), StudentsPlaceNotFound: BotError)
    } yield cnt
    res.value
  }

  override def takeAnotherPlace(
    student: StudentReadDomain,
    qsId:    Int,
    date:    LocalDate,
    place:   Int
  ): F[Either[BotError, Int]] = {
    val res = for {
      queueOpt <- EitherT.liftF(queueRepository.getQueue(qsId, date))
      queueId <- EitherT.liftF(
        queueOpt.fold(queueRepository.createQueue(qsId, date))(value => value.id.pure)
      )
      records <- EitherT.liftF(queueRepository.getRecords(queueId))
      _       <- EitherT.cond(records.exists(_.studentId == student.userId), (), StudentsPlaceNotFound: BotError)
      _       <- EitherT.liftF(addToQueueSemaphore.acquire)

      availablePlaces <- EitherT(getAvailablePlaces(student, qsId, date))

      _ <- EitherT
        .cond(availablePlaces.contains(place), (), TakePlaceFailed: BotError)
        .leftFlatMap(e => {
          for {
            _ <- EitherT.liftF(addToQueueSemaphore.release)
          } yield e
        }): EitherT[F, BotError, Unit]
      cnt <- EitherT.liftF(queueRepository.takeAnotherPlace(student.userId, queueId, place))
      _   <- EitherT.liftF(addToQueueSemaphore.release)

      _ <- EitherT.cond(cnt == 1, (), TakePlaceFailed: BotError)
    } yield place
    res.value
  }
}
