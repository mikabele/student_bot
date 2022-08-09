package syntax

import canoe.models.CallbackQuery
import canoe.models.messages.{DocumentMessage, TelegramMessage, TextMessage}
import cats.syntax.all._
import core.Messageable
import core.Messageable.MyTelegramMessage

object syntax {

  type Expect[A] = PartialFunction[Messageable, A]

  def callback(associatedMessage: Option[TelegramMessage]): Expect[CallbackQuery] = {
    case Messageable.MyCallbackQuery(query)
        if query.message.flatMap(msg => associatedMessage.map(_.messageId === msg.messageId)).getOrElse(false) =>
      query
  }

  def command(cmd: String): Expect[TextMessage] = {
    case Messageable.MyTelegramMessage(message: TextMessage) if message.text === ("/" + cmd) => message
  }

  def containing(str: String): Expect[TextMessage] = {
    case Messageable.MyTelegramMessage(message: TextMessage) if message.text === str => message
  }

  val textMessage:     Expect[TextMessage]     = { case Messageable.MyTelegramMessage(message: TextMessage) => message }
  val documentMessage: Expect[DocumentMessage] = { case MyTelegramMessage(message: DocumentMessage) => message }
}
