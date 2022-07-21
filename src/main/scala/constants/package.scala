import canoe.api.models.Keyboard
import cats.syntax.all._
import canoe.models.{KeyboardButton, ReplyKeyboardMarkup}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

package object constants {
  val mainMenuKeyboard: Keyboard.Reply = {
    val mainMenuBtns: Seq[Seq[KeyboardButton]] = Seq(
      Seq(KeyboardButton("Записаться в очередь"), KeyboardButton("Записать друга в очередь")),
      Seq(KeyboardButton("Записаться на другое место в очереди"), KeyboardButton("Выписаться из очереди")),
      Seq(KeyboardButton("Посмотреть список очередей"), KeyboardButton("Посмотреть очередь"))
    )
    val mainMenuMp: ReplyKeyboardMarkup = ReplyKeyboardMarkup(mainMenuBtns, resizeKeyboard = true.some)
    Keyboard.Reply(mainMenuMp)
  }

  val expRedis: FiniteDuration = 2.days
}
