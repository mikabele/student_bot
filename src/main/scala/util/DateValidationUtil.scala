package util

import canoe.models.messages.TextMessage
import canoe.syntax.partialFunctionOps

import java.time.LocalDate
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import scala.util.Try

object DateValidationUtil {
  val pattern: DateTimeFormatter = new DateTimeFormatterBuilder()
    .appendOptional(DateTimeFormatter.ofPattern("dd MM yyyy"))
    .appendOptional(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
    .appendOptional(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    .appendOptional(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    .toFormatter()

  def dateValidation(textMessage: syntax.syntax.Expect[TextMessage]): syntax.syntax.Expect[TextMessage] = {
    textMessage.when(msg =>
      Try(LocalDate.parse(msg.text, pattern)).fold(
        _ => false,
        d => d.isAfter(LocalDate.now())
      )
    )
  }
}
