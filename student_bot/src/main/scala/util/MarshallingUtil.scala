package util

import canoe.api.{callbackQueryApi, chatApi, Scenario, TelegramClient}
import canoe.models.{CallbackButtonSelected, Chat, Update}
import canoe.syntax._
import cats.syntax.all._
import cats.{Applicative, Monad, MonadError}
import domain.callback.{CallbackAnswer, CallbackQueryExt}
import domain.message.ReplyMessage
import fs2.Pipe
import io.circe.generic.auto._
import util.DecodingUtil.decodeCallbackData

object MarshallingUtil {

  //TODO add error handling
  def handleErrorInScenario[F[_]: TelegramClient: Monad](
    sc: Scenario[F, Unit]
  ): Scenario[F, Unit] = {
    sc.handleErrorWith(e => {
      println(e.getMessage)
      Scenario.done
    })
  }

  def replyMsg[F[_]: TelegramClient, M](chat: Chat, msg: ReplyMessage[M]): F[M] = {
    chat.send(msg.content, msg.replyToMessageId, msg.keyboard, msg.disableNotification)
  }

  private def defaultAnswer[F[_]: Monad: TelegramClient]: PartialFunction[CallbackQueryExt, F[Unit]] = { case _ =>
    Applicative[F].unit
  }

  def answerCallback[F[_]: TelegramClient](
    answers: Seq[PartialFunction[CallbackQueryExt, F[Unit]]]
  )(
    implicit me: MonadError[F, Throwable]
  ): Pipe[F, Update, Update] = {
    _.evalTap {
      case CallbackButtonSelected(_, query) =>
        val handlers = answers
          .reduce(_ orElse _)
          .orElse(defaultAnswer)
        // TODO check did this solution solve the problem with outdated messages from previos run when server was crushed
        val res = for {
          answer <- me.fromEither(decodeCallbackData[CallbackAnswer](query))
          ext     = CallbackQueryExt(query, answer)
          _      <- handlers(ext)
        } yield ()
        res.handleErrorWith(e => {
          println(e.getMessage)
          query.alert(e.getMessage).void
        })
      case _ => Applicative[F].unit
    }
  }
}
