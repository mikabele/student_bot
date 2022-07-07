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
import error.impl.student.StudentNotFound
import repository.{QueueRepository, StudentRepository}
import service.QueueService

import java.util.Date

class QueueServiceImpl[F[_]: Monad](
  studentRepository:   StudentRepository[F],
  queueRepository:     QueueRepository[F],
  addToQueueSemaphore: Semaphore[F]
) extends QueueService[F] {
  override def getQueueSeries(studentId: Int): F[Either[BotError, List[QueueSeries]]] = {
    val res = for {
      student <- EitherT.fromOptionF(studentRepository.getStudentById(studentId), StudentNotFound: BotError)
      qss     <- EitherT.right[BotError](queueRepository.getQueueSeries(student))
    } yield qss

    res.value
  }

  private def getPlaceFromOption(
    studentId: Int,
    qsId:      Int,
    date:      Date,
    option:    AddToQueueOption,
    place:     Option[Int]
  ): F[Either[BotError, Int]] = {
    for {
      place <- option match {
        case AddToQueueOption.PushFront =>
          val res = for {
            availablePlaces <- EitherT(getAvailablePlaces(studentId, qsId, date))
          } yield availablePlaces.min
          res.value
        case AddToQueueOption.PushBack =>
          val res = for {
            availablePlaces <- EitherT(getAvailablePlaces(studentId, qsId, date))
          } yield availablePlaces.max
          res.value
        case AddToQueueOption.TakePlace =>
          val res = for {
            availablePlaces <- EitherT(getAvailablePlaces(studentId, qsId, date))
            _                = println(availablePlaces)
            placeE          <- EitherT.fromOption(place, InvalidOption: BotError)
            _                = println(placeE)
            _               <- EitherT.cond(availablePlaces.contains(placeE), (), TakePlaceFailed: BotError)
          } yield placeE
          res.value
        case _ => (InvalidOption: BotError).asLeft[Int].pure[F]
      }
    } yield place
  }

  override def addToQueue(
    studentId: Int,
    qsId:      Int,
    date:      Date,
    option:    queue.AddToQueueOption,
    place:     Option[Int]
  ): F[Either[BotError, Int]] = {
    val res = for {
      queueOpt <- EitherT.liftF(queueRepository.getQueue(qsId, date))
      queueId <- EitherT.liftF(
        queueOpt.fold(queueRepository.createQueue(qsId, date))(value => value.id.pure)
      )
      records <- EitherT.liftF(queueRepository.getRecords(queueId))
      _       <- EitherT.cond(!records.exists(_.studentId == studentId), (), StudentAlreadyTakePlaceInQueue: BotError)
      _       <- EitherT.liftF(addToQueueSemaphore.acquire)
      _        = println("Mutex acquired")
      place <- EitherT(getPlaceFromOption(studentId, qsId, date, option, place)).leftFlatMap(e => {
        for {
          _   <- EitherT.liftF(addToQueueSemaphore.release)
          _    = println("Mutex released")
          res <- EitherT.left[Int](e.pure)
        } yield res
      })
      cnt <- EitherT.liftF(queueRepository.takePlace(studentId, queueId, place))
      _   <- EitherT.liftF(addToQueueSemaphore.release)
      _    = println("Mutex released")
      _   <- EitherT.cond(cnt == 1, (), TakePlaceFailed: BotError)
    } yield place
    res.value
  }

  override def getAvailablePlaces(studentId: Int, qsId: Int, date: Date): F[Either[BotError, List[Int]]] = {
    val res = for {
      student  <- EitherT.fromOptionF(studentRepository.getStudentById(studentId), StudentNotFound: BotError)
      queueOpt <- EitherT.liftF(queueRepository.getQueue(qsId, date)): EitherT[F, BotError, Option[QueueDbReadDomain]]
      queueId <- EitherT.liftF(
        queueOpt.fold(queueRepository.createQueue(qsId, date))(value => value.id.pure)
      ): EitherT[F, BotError, Int]
      cnt      <- EitherT.liftF(studentRepository.getGroupSize(student)): EitherT[F, BotError, Int]
      allPlaces = (1 to cnt).toList
      records  <- EitherT.liftF(queueRepository.getRecords(queueId)):     EitherT[F, BotError, List[QueueRecordReadDomain]]
    } yield allPlaces.diff(records.map(_.place))

    res.value
  }

  override def getQueue(qsId: Int, date: Date): F[Either[BotError, Queue]] = {
    val res = for {
      queue      <- EitherT.fromOptionF(queueRepository.getQueue(qsId, date), QueueNotFound: BotError)
      records    <- EitherT.liftF(queueRepository.getRecords(queue.id)): EitherT[F, BotError, List[QueueRecordReadDomain]]
      studentIds <- EitherT.fromOption(records.map(_.studentId).toNel, QueueNotFound: BotError)
      students <- EitherT
        .liftF(studentRepository.getStudentsByIds(studentIds)): EitherT[F, BotError, List[
        StudentReadDomain
      ]]
      queueRecords = records.map(qr => QueueRecord(qr.id, qr.place, students.find(_.userId == qr.studentId).get))
    } yield Queue(queue.id, queue.queueSeriesId, queue.date, queueRecords)

    res.value
  }
}
