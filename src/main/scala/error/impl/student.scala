package error.impl

import error.BotError
import util.bundle.StringFormatExtension._

import java.util.ResourceBundle

object student {
  final case object StudentNotFound extends BotError {
    override def resourceString(bundle: ResourceBundle): String =
      bundle.getFormattedString("error.student.student_not_found")
  }
}
