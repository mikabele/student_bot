package conversions

import canoe.models.{CallbackButtonSelected, MessageReceived, Update}
import fs2.Pipe
import core.Messageable
import core.Messageable.{MyCallbackQuery, MyTelegramMessage}

object pipes {
  private def messageReceivedPF: PartialFunction[Update, Messageable] = { case MessageReceived(_, message) =>
    MyTelegramMessage(message)
  }

  private def callbackButtonSelectedPF: PartialFunction[Update, Messageable] = {
    case CallbackButtonSelected(_, query) =>
      MyCallbackQuery(query)
  }

  def messageables[F[_]]: Pipe[F, Update, Messageable] =
    _.collect((messageReceivedPF :: callbackButtonSelectedPF :: Nil).reduce(_ orElse _))
}
