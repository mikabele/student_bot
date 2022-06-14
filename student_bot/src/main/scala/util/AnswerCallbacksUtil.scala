package util

import canoe.api.TelegramClient
import canoe.models.{CallbackButtonSelected, CallbackQuery, Update}
import cats.{Applicative, Monad}
import fs2.Pipe

object AnswerCallbacksUtil {

  private def default[F[_]: Monad]: PartialFunction[CallbackQuery, F[Unit]] = { case _ => Applicative[F].unit }

  def answer[F[_]: Monad: TelegramClient](
    answers: Seq[PartialFunction[CallbackQuery, F[Unit]]]
  ): Pipe[F, Update, Update] = {
    _.evalTap {
      case CallbackButtonSelected(_, query) =>
        answers
          .foldRight(default)(_ orElse _)(query)

      case _ => Applicative[F].unit
    }
  }
}
