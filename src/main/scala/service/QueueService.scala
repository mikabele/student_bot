package service

import cats.effect.Concurrent
import cats.effect.std.Semaphore
import cats.syntax.all._
import domain.queue
import domain.queue.{AddToQueueOption, Queue}
import domain.user.StudentReadDomain
import error.BotError
import org.typelevel.log4cats.Logger
import repository.{QueueRepository, StudentRepository}
import service.impl.QueueServiceImpl

import java.time.LocalDate

trait QueueService[F[_]] {
  def takeAnotherPlace(
    studentReadDomain: StudentReadDomain,
    qsId:              Int,
    date:              LocalDate,
    place:             Int
  ): F[Either[BotError, Int]]

  def removeFromQueue(studentReadDomain: StudentReadDomain, qsId: Int, date: LocalDate): F[Either[BotError, Int]]

  def addToQueue(
    studentReadDomain: StudentReadDomain,
    qsId:              Int,
    date:              LocalDate,
    option:            AddToQueueOption,
    place:             Option[Int] = None
  ): F[Either[BotError, Int]]

  def getQueueSeries(student: StudentReadDomain): F[List[queue.QueueSeries]]

  def getAvailablePlaces(student: StudentReadDomain, qsId: Int, date: LocalDate): F[Either[BotError, List[Int]]]

  def getQueue(qsId: Int, date: LocalDate): F[Either[BotError, Queue]]
}

object QueueService {
  def of[F[_]: Concurrent: Logger](
    studentRepository: StudentRepository[F],
    queueRepository:   QueueRepository[F]
  ): F[QueueService[F]] = {
    for {
      addToQueueSemaphore <- Semaphore[F](1)
    } yield new QueueServiceImpl[F](studentRepository, queueRepository, addToQueueSemaphore)
  }
}
