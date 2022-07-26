package error

import java.util.ResourceBundle

trait BotError extends Throwable {
  def resourceString(bundle: ResourceBundle): String
}
