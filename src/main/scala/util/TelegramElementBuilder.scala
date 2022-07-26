package util

import canoe.api.models.Keyboard
import canoe.models.{InlineKeyboardButton, InlineKeyboardMarkup}
import cats.syntax.all._
import io.circe.generic.auto._
import io.circe.syntax._

object TelegramElementBuilder {

  def buildInlineKeyboard[M](
    data:             Seq[Seq[M]],
    textDataFunc:     M => String,
    callbackDataFunc: M => String
  ): Keyboard.Inline = {
    val buttons =
      data.map(_.map(d => InlineKeyboardButton.callbackData(text = textDataFunc(d), cbd = callbackDataFunc(d))))
    val ikm = InlineKeyboardMarkup(buttons)
    Keyboard.Inline(ikm)
  }

}
