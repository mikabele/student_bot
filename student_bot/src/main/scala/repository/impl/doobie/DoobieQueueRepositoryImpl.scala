package repository.impl.doobie

import cats.data.OptionT
import cats.effect.Sync
import cats.syntax.all._
import domain.queue._
import domain.user.StudentReadDomain
import doobie.Fragment
import doobie.implicits._
import doobie.util.transactor.Transactor
import repository.QueueRepository
import util.MappingUtil.DbDomainMappingUtil._

import java.util.Date

class DoobieQueueRepositoryImpl[F[_]: Sync](tx: Transactor[F]) extends QueueRepository[F] {

  private val getQueueSeriesQuery = Fragment.const("SELECT id,name,\"group\" FROM queue_series")
  private val takePlaceQuery      = Fragment.const("INSERT INTO record(queue_id,student_id,place) VALUES")
  private val getRecordsQuery     = Fragment.const("SELECT id,place,student_id,queue_id FROM record")
  private val getQueueQuery       = Fragment.const("SELECT id,queue_series_id,date FROM queue")
  private val createQueueQuery    = Fragment.const("INSERT INTO queue(queue_series_id,date) VALUES")

  override def takePlace(studentId: Int, queueId: Int, place: Int): F[Int] = {
    (takePlaceQuery ++ fr"($queueId,$studentId,$place)").update.run.transact(tx)
  }

  override def getRecords(queueId: Int): F[List[QueueRecordReadDomain]] = {
    (getRecordsQuery ++ fr" WHERE queue_id = $queueId").query[QueueRecordReadDomain].to[List].transact(tx)
  }

  override def getQueueSeries(student: StudentReadDomain): F[List[QueueSeries]] = {
    for {
      qs <- (getQueueSeriesQuery ++ Fragment.const("WHERE \"group\" = ") ++ fr"${student.group}")
        .query[QueueSeriesDbReadDomain]
        .to[List]
        .transact(tx)
    } yield dbReadQueueSeriesToQueueSeries(qs, List.empty[Queue])
  }

  override def createQueue(qsId: Int, date: Date): F[Int] = {
    (createQueueQuery ++ fr"($qsId,$date)").update.withUniqueGeneratedKeys[Int]("id").transact(tx)
  }

  //TODO dummy method, remove it and replace with getId
  override def getQueue(qsId: Int, date: Date): F[Option[Queue]] = {
    val res = for {
      queue <- OptionT(
        (getQueueQuery ++ fr"WHERE queue_series_id = $qsId AND date = $date")
          .query[QueueDbReadDomain]
          .option
          .transact(tx)
      )
    } yield Queue(queue.id, queue.queueSeriesId, queue.date, List.empty[QueueRecord])

    res.value
  }
}
