package domain

import domain.user.StudentReadDomain
import enumeratum.{CirceEnum, Enum, EnumEntry}

import java.util.Date

object queue {
  final case class Queue(
    id:            Int,
    queueSeriesId: Int,
    date:          Date,
    records:       List[QueueRecord]
  )

  final case class QueueDbReadDomain(
    id:            Int,
    queueSeriesId: Int,
    date:          Date
  )

  final case class QueueSeries(
    id:     Int,
    name:   String,
    group:  Int,
    queues: List[Queue]
  )

  final case class QueueSeriesDbReadDomain(
    id:    Int,
    name:  String,
    group: Int
  )

  final case class QueueRecord(
    id:      Int,
    place:   Int,
    student: StudentReadDomain
  )

  final case class QueueRecordReadDomain(
    id:        Int,
    place:     Int,
    studentId: Int,
    queueId:   Int
  )

  sealed trait AddToQueueOption extends EnumEntry

  object AddToQueueOption extends Enum[AddToQueueOption] with CirceEnum[AddToQueueOption] {
    final case object PushBack extends AddToQueueOption
    final case object PushFront extends AddToQueueOption
    final case object TakePlace extends AddToQueueOption

    override def values: IndexedSeq[AddToQueueOption] = findValues
  }
}
