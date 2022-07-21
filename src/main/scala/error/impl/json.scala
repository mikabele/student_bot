package error.impl

import error.BotError

object json {
  final case object DecodingError extends BotError {
    override def getMessage: String = "Ошибка декодирования сообщения"
  }
}
