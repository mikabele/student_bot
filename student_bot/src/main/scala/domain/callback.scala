package domain

import canoe.models.CallbackQuery
import domain.queue.AddToQueueOption
import enumeratum.values._

import java.util.Date

object callback {

  sealed abstract class Flow(val value: Int)

  object Flow {

    final case class AuthFlow(
      course:    Option[Int] = None,
      group:     Option[Int] = None,
      studentId: Option[Int] = None
    ) extends Flow(1)

    final case class AddToQueueFlow(
      studentId:        Option[Int]              = None,
      queueSeriesId:    Option[Int]              = None,
      date:             Option[Date]             = None,
      addToQueueOption: Option[AddToQueueOption] = None,
      place:            Option[Int]              = None
    ) extends Flow(2)

    val values: Map[Int, Object] = Map(1 -> AuthFlow, 2 -> AddToQueueFlow)
  }

  final case class CallbackAnswer(
    flowId: Int,
    step:   Int,
    value:  String
  )

  final case class CallbackQueryExt(
    query:  CallbackQuery,
    answer: CallbackAnswer
  )
}
