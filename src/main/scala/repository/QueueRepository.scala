package repository

import cats.effect.kernel.Async
import domain.queue
import domain.queue.{QueueDbReadDomain, QueueSeries}
import domain.user.StudentReadDomain
import doobie.util.transactor.Transactor
import org.typelevel.log4cats.Logger
import repository.impl.doobie.DoobieQueueRepositoryImpl

import java.time.LocalDate

trait QueueRepository[F[_]] {
  def removeFromQueue(queueId: Int, studentId: Int): F[Int]

  def takeAnotherPlace(studentId: Int, queueId: Int, place: Int): F[Int]

  def createQueue(qsId: Int, date: LocalDate): F[Int]

  def getQueue(qsId: Int, date: LocalDate): F[Option[QueueDbReadDomain]]

  def getQueueSeries(student: StudentReadDomain): F[List[QueueSeries]]

  def takePlace(studentId: Int, queueId: Int, place: Int): F[Int]

  def getRecords(queueId: Int): F[List[queue.QueueRecordReadDomain]]
}

object QueueRepository {
  def of[F[_]: Async: Logger](tx: Transactor[F]): QueueRepository[F] = {
    new DoobieQueueRepositoryImpl[F](tx)
  }
}
