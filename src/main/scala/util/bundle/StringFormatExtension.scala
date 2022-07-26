package util.bundle

import constants.DEFAULT_MSG_CONTENT

import java.util.ResourceBundle
import scala.util.Try

sealed trait StringFormatExtension[A] {
  def getFString(source: A, name: String, args: Seq[Any]): String
}

object StringFormatExtension {
  implicit val rbeIml: StringFormatExtension[ResourceBundle] = new StringFormatExtension[ResourceBundle] {
    override def getFString(source: ResourceBundle, name: String, args: Seq[Any]): String = {
      val str = Try(source.getString(name))
      str.map(_.format(args: _*)).getOrElse(DEFAULT_MSG_CONTENT)
    }
  }

  implicit class StringExtension[A](value: A) {
    def getFormattedString(name: String, args: Any*)(implicit sfe: StringFormatExtension[A]): String =
      sfe.getFString(value, name, args.toList)
  }
}
