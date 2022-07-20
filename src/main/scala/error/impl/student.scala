package error.impl

import error.BotError

object student {
  final case object StudentNotFound extends BotError {
    override def getMessage: String = "Студент не найден"
  }
}
