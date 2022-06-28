package service.impl

import cats.Monad
import cats.data.EitherT
import cats.syntax.all._
import domain.queue
import domain.queue.{AddToQueueOption, Queue, QueueRecordReadDomain, QueueSeries}
import error.BotError
import error.impl.queue._
import error.impl.student.StudentNotFound
import repository.{QueueRepository, StudentRepository}
import service.QueueService

import java.util.Date

class QueueServiceImpl[F[_]: Monad](studentRepository: StudentRepository[F], queueRepository: QueueRepository[F])
  extends QueueService[F] {
  override def getQueueSeries(studentId: Int): F[Either[BotError, List[QueueSeries]]] = {
    //List(QueueSeries(1, "Рафеенко", 12, List.empty[Queue])).pure
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
    option match {
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
      case AddToQueueOption.TakePlace => Either.fromOption(place, InvalidOption: BotError).pure
      case _                          => (InvalidOption: BotError).asLeft[Int].pure[F]
    }
  }

  override def addToQueue(
    studentId: Int,
    qsId:      Int,
    date:      Date,
    option:    queue.AddToQueueOption,
    place:     Option[Int]
  ): F[Either[BotError, Int]] = {
    //1.asRight[BotError].pure
    val res = for {
      queueOpt <- EitherT.liftF(queueRepository.getQueue(qsId, date))
      queueId <- EitherT.liftF(
        queueOpt.fold(queueRepository.createQueue(qsId, date))(value => value.id.pure)
      )
      place <- EitherT(getPlaceFromOption(studentId, qsId, date, option, place))
      cnt   <- EitherT.liftF(queueRepository.takePlace(studentId, queueId, place))
      _     <- EitherT.cond(cnt == 1, (), TakePlaceFailed): EitherT[F, BotError, Unit]
    } yield place
    res.value
  }

  override def getAvailablePlaces(studentId: Int, qsId: Int, date: Date): F[Either[BotError, List[Int]]] = {
    //    (1 to 25).toList.pure
    val res = for {
      student  <- EitherT.fromOptionF(studentRepository.getStudentById(studentId), StudentNotFound: BotError)
      queueOpt <- EitherT.liftF(queueRepository.getQueue(qsId, date)): EitherT[F, BotError, Option[Queue]]
      queueId <- EitherT.liftF(
        queueOpt.fold(queueRepository.createQueue(qsId, date))(value => value.id.pure)
      ): EitherT[F, BotError, Int]
      // TODO fix compile issue
      cnt      <- EitherT.liftF(studentRepository.getGroupSize(student)): EitherT[F, BotError, Int]
      allPlaces = (1 to cnt).toList
      records  <- EitherT.liftF(queueRepository.getRecords(queueId)):     EitherT[F, BotError, List[QueueRecordReadDomain]]
    } yield allPlaces.diff(records.map(_.place))

    res.value
  }
}
