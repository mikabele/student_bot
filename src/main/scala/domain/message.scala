package domain

import canoe.api.models.Keyboard
import canoe.models.outgoing.MessageContent
import constants.mainMenuKeyboard

object message {
  final case class ReplyMessage[M](
    content:             MessageContent[M],
    replyToMessageId:    Option[Int] = None,
    keyboard:            Keyboard    = mainMenuKeyboard,
    disableNotification: Boolean     = false
  )
}
