package error.impl

import cats.data.NonEmptyList
import domain.user.Role
import error.BotError
import util.bundle.StringFormatExtension.StringExtension
import cats.syntax.all._

import java.util.ResourceBundle

object admin {
  final case object EmptyDataFile extends BotError {
    override def resourceString(bundle: ResourceBundle): String =
      bundle.getFormattedString("error.admin.empty_data_file")
  }

  final case class NotEnoughPermissions(expectedRoles: NonEmptyList[Role]) extends BotError {
    override def resourceString(bundle: ResourceBundle): String =
      bundle.getFormattedString("error.admin.not_enough_permissions", expectedRoles.map(_.toString |+| "\n").reduce(_ |+| _))
  }
}
