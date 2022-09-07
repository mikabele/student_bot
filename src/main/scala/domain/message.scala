package domain

import canoe.api.models.Keyboard
import canoe.models.outgoing.MessageContent

object message {
  final case class ReplyMessage[M](
    content:             MessageContent[M],
    replyToMessageId:    Option[Int] = None,
    keyboard:            Keyboard    = Keyboard.Unchanged,
    disableNotification: Boolean     = false
  )
}
