package service

import cats.Monad
import domain.queue
import domain.queue.AddToQueueOption
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
}

object QueueService {
  def of[F[_]: Monad](studentRepository: StudentRepository[F], queueRepository: QueueRepository[F]): QueueService[F] = {
    new QueueServiceImpl[F](studentRepository, queueRepository)
  }
}
