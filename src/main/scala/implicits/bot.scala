package implicits

import canoe.models.Messageable
import canoe.models.messages.TextMessage
import canoe.syntax.Expect
import cats.implicits.catsSyntaxEq
import util.bundle.ResourceBundleUtil

object bot {
  def containingWithBundle(key: String, resourceBundleUtil: ResourceBundleUtil): Expect[TextMessage] = {
    case Messageable.MyTelegramMessage(message: TextMessage)
        if message.text === resourceBundleUtil.getBundle(message.from.get.languageCode.get).getString(key) =>
      message
  }
}
