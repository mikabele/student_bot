package util

import canoe.api.models.Keyboard
import canoe.models.{InlineKeyboardButton, InlineKeyboardMarkup}
import canoe.syntax._
import cats.syntax.all._
import domain.callback.CallbackAnswer
import domain.callback.Flow.AddToQueueFlow
import domain.queue.AddToQueueOption
import io.circe.generic.auto._
import io.circe.syntax._

object TelegramElementBuilder {

  def buildInlineKeyboard[M](
    data:             Seq[M],
    textDataFunc:     M => String,
    callbackDataFunc: M => CallbackAnswer
  ): Keyboard.Inline = {
    val buttons = data.map(d =>
      InlineKeyboardButton.callbackData(text = textDataFunc(d), cbd = callbackDataFunc(d).asJson.toString)
    )
    val ikm = InlineKeyboardMarkup.singleColumn(buttons)
    Keyboard.Inline(ikm)
  }

  def buildAddToQueueOptionsKeyboard(flow: AddToQueueFlow): Keyboard.Inline = {
    val pushFrontBtn = InlineKeyboardButton(
      "Записаться на первое свободное место",
      callbackData = CallbackAnswer(flow.value, 4, AddToQueueOption.PushFront.toString).asJson.toString().some
    )
    val pushBackBtn = InlineKeyboardButton(
      "Записаться на последнее свободное место",
      callbackData = CallbackAnswer(flow.value, 4, AddToQueueOption.PushBack.toString).asJson.toString().some
    )
    val takePlaceBtn =
      InlineKeyboardButton(
        "Выбрать любое свободное место",
        callbackData = CallbackAnswer(flow.value, 2, AddToQueueOption.TakePlace.toString).asJson.toString().some
      )
    val ikm = InlineKeyboardMarkup.singleColumn(Seq(pushFrontBtn, pushBackBtn, takePlaceBtn))
    Keyboard.Inline(ikm)

  }
}
