package error

trait BotError extends Throwable {
  def getMessage: String
}
