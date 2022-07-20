package error.impl

import error.BotError

object queue {
  final case object InvalidOption extends BotError {
    override def getMessage: String = "Выбрана неправильная опция"
  }

  final case object TakePlaceFailed extends BotError {
    override def getMessage: String = "Место уже занято, попробуйте снова."
  }

  final case object CreateQueueFailed extends BotError {
    override def getMessage: String = "Не удалось создать очередь на эту дату"
  }

  final case object StudentAlreadyTakePlaceInQueue extends BotError {
    override def getMessage: String = "Студент уже записан в очередь."
  }

  final case object QueueNotFound extends BotError {
    override def getMessage: String = "На такую дату очереди еще не существует."
  }
}