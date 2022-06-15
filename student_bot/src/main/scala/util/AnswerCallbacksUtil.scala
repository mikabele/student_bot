package util

import canoe.api.TelegramClient
import canoe.methods.queries.AnswerCallbackQuery
import canoe.models.{CallbackButtonSelected, CallbackQuery, Update}
import canoe.syntax._
import cats.syntax.all._
import cats.{Applicative, Monad}
import fs2.Pipe

object AnswerCallbacksUtil {

  private def defaultAnswer[F[_]: Monad: TelegramClient]: PartialFunction[CallbackQuery, F[Unit]] = { case query =>
    Applicative[F].unit
  }

  def answerCallback[F[_]: Monad: TelegramClient](
    answers: Seq[PartialFunction[CallbackQuery, F[Unit]]]
  ): Pipe[F, Update, Update] = {
    _.evalTap {
      case CallbackButtonSelected(_, query) =>
        val ans = answers
          .foldRight(defaultAnswer)(_ orElse _)
        // TODO check did this solution solve the problem with outdated messages from previos run when server was crushed
        for {
          _ <- ans(query)
          _ <- AnswerCallbackQuery.finish(query.id).copy(cacheTime = 1.some).call
        } yield ()

      case _ => Applicative[F].unit
    }
  }
}
