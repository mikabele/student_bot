package util

import canoe.api.{chatApi, TelegramClient}
import canoe.models.Chat
import domain.message.ReplyMessage

object MarshallingUtil {
  def replyMsg[F[_]: TelegramClient, M](chat: Chat, msg: ReplyMessage[M]): F[M] = {
    chat.send(msg.content, msg.replyToMessageId, msg.keyboard, msg.disableNotification)
  }
}
