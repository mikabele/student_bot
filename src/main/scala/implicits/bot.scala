package implicits

import canoe.models.messages.TextMessage
import cats.implicits.catsSyntaxEq
import core.Messageable
import syntax.syntax.Expect
import util.bundle.ResourceBundleUtil

object bot {
  def containingWithBundle(key: String, resourceBundleUtil: ResourceBundleUtil): Expect[TextMessage] = {
    case Messageable.MyTelegramMessage(message: TextMessage)
        if message.text === resourceBundleUtil.getBundle(message.from.get.languageCode.get).getString(key) =>
      message
  }
}
