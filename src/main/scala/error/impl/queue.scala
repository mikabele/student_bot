package error.impl

import error.BotError
import util.bundle.StringFormatExtension._

import java.util.ResourceBundle

object queue {
  final case object InvalidOption extends BotError {
    override def resourceString(bundle: ResourceBundle): String =
      bundle.getFormattedString("error.queue.invalid_option")
  }

  final case object TakePlaceFailed extends BotError {
    override def resourceString(bundle: ResourceBundle): String =
      bundle.getFormattedString("error.queue.take_place_failed")
  }

  final case object CreateQueueFailed extends BotError {
    override def resourceString(bundle: ResourceBundle): String =
      bundle.getFormattedString("error.queue.create_queue_failed")
  }

  final case object StudentAlreadyTakePlaceInQueue extends BotError {
    override def resourceString(bundle: ResourceBundle): String =
      bundle.getFormattedString("error.queue.student_already_took_place")
  }

  final case object QueueNotFound extends BotError {
    override def resourceString(bundle: ResourceBundle): String =
      bundle.getFormattedString("error.queue.queue_not_found")
  }

  final case object StudentsPlaceNotFound extends BotError {
    override def resourceString(bundle: ResourceBundle): String =
      bundle.getFormattedString("error.queue.students_place_not_found")
  }
}
