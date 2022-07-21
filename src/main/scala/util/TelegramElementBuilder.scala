package util

import canoe.api.models.Keyboard
import canoe.models.{InlineKeyboardButton, InlineKeyboardMarkup}
import cats.syntax.all._
import domain.queue.AddToQueueOption
import io.circe.generic.auto._
import io.circe.syntax._

object TelegramElementBuilder {

  def buildInlineKeyboard[M](
    data:             Seq[M],
    textDataFunc:     M => String,
    callbackDataFunc: M => String
  ): Keyboard.Inline = {
    val buttons = data.map(d => InlineKeyboardButton.callbackData(text = textDataFunc(d), cbd = callbackDataFunc(d)))
    val ikm     = InlineKeyboardMarkup.singleColumn(buttons)
    Keyboard.Inline(ikm)
  }

  def buildAddToQueueOptionsKeyboard: Keyboard.Inline = {
    val pushFrontBtn = InlineKeyboardButton(
      "Записаться на первое свободное место",
      callbackData = AddToQueueOption.PushFront.toString.some
    )
    val pushBackBtn = InlineKeyboardButton(
      "Записаться на последнее свободное место",
      callbackData = AddToQueueOption.PushBack.toString.some
    )
    val takePlaceBtn =
      InlineKeyboardButton(
        "Выбрать любое свободное место",
        callbackData = AddToQueueOption.TakePlace.toString.some
      )
    val ikm = InlineKeyboardMarkup.singleColumn(Seq(pushFrontBtn, pushBackBtn, takePlaceBtn))
    Keyboard.Inline(ikm)

  }
}
