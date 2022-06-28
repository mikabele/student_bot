package repository

import cats.effect.kernel.Async
import domain.queue
import domain.queue.{Queue, QueueSeries}
import domain.user.StudentReadDomain
import doobie.util.transactor.Transactor
import repository.impl.doobie.DoobieQueueRepositoryImpl

import java.util.Date

trait QueueRepository[F[_]] {
  def createQueue(qsId: Int, date: Date): F[Int]

  def getQueue(qsId: Int, date: Date): F[Option[Queue]]

  def getQueueSeries(student: StudentReadDomain): F[List[QueueSeries]]

  def takePlace(studentId: Int, queueId: Int, place: Int): F[Int]

  def getRecords(queueId: Int): F[List[queue.QueueRecordReadDomain]]
}

object QueueRepository {
  def of[F[_]: Async](tx: Transactor[F]): QueueRepository[F] = {
    new DoobieQueueRepositoryImpl[F](tx)
  }
}
