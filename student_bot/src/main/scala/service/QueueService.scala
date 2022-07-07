package service

import cats.effect.Concurrent
import cats.effect.std.Semaphore
import cats.syntax.all._
import domain.queue
import domain.queue.{AddToQueueOption, Queue}
import error.BotError
import repository.{QueueRepository, StudentRepository}
import service.impl.QueueServiceImpl

import java.util.Date

trait QueueService[F[_]] {
  def addToQueue(
    studentId: Int,
    qsId:      Int,
    date:      Date,
    option:    AddToQueueOption,
    place:     Option[Int] = None
  ): F[Either[BotError, Int]]

  def getQueueSeries(studentId: Int): F[Either[BotError, List[queue.QueueSeries]]]

  def getAvailablePlaces(studentId: Int, qsId: Int, date: Date): F[Either[BotError, List[Int]]]

  def getQueue(qsId: Int, date: Date): F[Either[BotError, Queue]]
}

object QueueService {
  def of[F[_]: Concurrent](
    studentRepository: StudentRepository[F],
    queueRepository:   QueueRepository[F]
  ): F[QueueService[F]] = {
    for {
      addToQueueSemaphore <- Semaphore[F](1)
    } yield new QueueServiceImpl[F](studentRepository, queueRepository, addToQueueSemaphore)
  }
}
