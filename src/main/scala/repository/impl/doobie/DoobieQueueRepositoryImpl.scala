package repository.impl.doobie

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.syntax.all._
import domain.queue._
import domain.user.StudentReadDomain
import doobie.Fragment
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragments.values
import doobie.util.transactor.Transactor
import org.typelevel.log4cats.Logger
import repository.QueueRepository
import repository.impl.doobie.logger.logger._
import util.MappingUtil.DbDomainMappingUtil._

import java.time.LocalDate

class DoobieQueueRepositoryImpl[F[_]: Sync: Logger](tx: Transactor[F]) extends QueueRepository[F] {

  private val getQueueSeriesQuery    = Fragment.const("SELECT id,name,university,course,\"group\" FROM queue_series")
  private val takePlaceQuery         = Fragment.const("INSERT INTO record(queue_id,student_id,place) VALUES")
  private val getRecordsQuery        = Fragment.const("SELECT id,place,student_id,queue_id FROM record")
  private val getQueueQuery          = Fragment.const("SELECT id,queue_series_id,date FROM queue")
  private val createQueueQuery       = Fragment.const("INSERT INTO queue(queue_series_id,date) VALUES")
  private val deleteRecordQuery      = Fragment.const("DELETE FROM record ")
  private val updateRecordQuery      = Fragment.const("UPDATE record ")
  private val createQueueSeriesQuery = Fragment.const("INSERT INTO queue_series(name,university,course,\"group\") ")

  override def takePlace(studentId: Int, queueId: Int, place: Int): F[Int] = {
    (takePlaceQuery ++ fr"($queueId,$studentId,$place)").update.run.transact(tx)
  }

  override def getRecords(queueId: Int): F[List[QueueRecordReadDomain]] = {
    (getRecordsQuery ++ fr" WHERE queue_id = $queueId ORDER BY place")
      .query[QueueRecordReadDomain]
      .to[List]
      .transact(tx)
  }

  override def getQueueSeries(student: StudentReadDomain): F[List[QueueSeriesReadDomain]] = {
    for {
      qs <-
        (getQueueSeriesQuery ++ fr" WHERE university = ${student.university} AND course = ${student.course}" ++ Fragment
          .const("AND \"group\" = ") ++ fr"${student.group}")
          .query[QueueSeriesDbReadDomain]
          .to[List]
          .transact(tx)
    } yield dbReadQueueSeriesToQueueSeries(qs, List.empty[Queue])
  }

  override def createQueue(qsId: Int, date: LocalDate): F[Int] = {
    (createQueueQuery ++ fr"($qsId,$date)").update.withUniqueGeneratedKeys[Int]("id").transact(tx)
  }

  override def getQueue(qsId: Int, date: LocalDate): F[Option[QueueDbReadDomain]] = {
    (getQueueQuery ++ fr"WHERE queue_series_id = $qsId AND date = $date")
      .query[QueueDbReadDomain]
      .option
      .transact(tx)
  }

  override def removeFromQueue(queueId: Int, studentId: Int): F[Int] = {
    (deleteRecordQuery ++ fr" WHERE queue_id = $queueId AND student_id = $studentId").update.run.transact(tx)
  }

  override def takeAnotherPlace(studentId: Int, queueId: Int, place: Int): F[Int] = {
    (updateRecordQuery ++ fr"SET place = $place " ++ fr" WHERE queue_id = $queueId AND student_id = $studentId").update.run
      .transact(tx)
  }

  override def addQueueSeries(nel: NonEmptyList[QueueSeriesCreateDomain]): F[Int] = {
    (createQueueSeriesQuery ++ values(nel)).update.run.transact(tx)
  }
}
