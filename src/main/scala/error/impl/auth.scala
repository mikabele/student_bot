package error.impl

import error.BotError

object auth {
  final case object UserNotFound extends BotError {
    override def getMessage: String = "Oops, мы не смогли найти твоего пользователя, попробуй еще раз."
  }

  final case object UsernameNotFound extends BotError {
    override def getMessage: String = "Oops, мы не смогли найти ,твой никнейм попробуй еще раз."
  }

  final case object AlreadyAuthorizedUser extends BotError {
    override def getMessage: String = "Ух, мы с тобой уже знакомы. Я готов тебе помочь."
  }

  final case object ForbiddenAuthUser extends BotError {
    override def getMessage: String = "Этот студент уже авторизовался, выбери другое имя."
  }

  final case object NonAuthorizedUser extends BotError {
    override def getMessage: String = "Пользователь не авторизован."
  }
}
