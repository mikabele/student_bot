package error.impl

import error.BotError
import util.bundle.StringFormatExtension._

import java.util.ResourceBundle

object auth {
  final case object UserNotFound extends BotError {
    override def resourceString(bundle: ResourceBundle): String =
      bundle.getFormattedString("error.auth.user_not_found")
  }

  final case object UsernameNotFound extends BotError {
    override def resourceString(bundle: ResourceBundle): String =
      bundle.getFormattedString("error.auth.username_not_found")
  }

  final case object AlreadyAuthorizedUser extends BotError {
    override def resourceString(bundle: ResourceBundle): String =
      bundle.getFormattedString("error.auth.already_authorized_user")
  }

  final case object ForbiddenAuthUser extends BotError {
    override def resourceString(bundle: ResourceBundle): String =
      bundle.getFormattedString("error.auth.forbidden_auth_user")
  }

  final case object NonAuthorizedUser extends BotError {
    override def resourceString(bundle: ResourceBundle): String =
      bundle.getFormattedString("error.auth.non_authorized_user")
  }
}
