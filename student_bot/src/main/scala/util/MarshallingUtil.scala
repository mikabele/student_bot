package util

import canoe.api._
import canoe.methods.chats.PinChatMessage
import canoe.models.messages.TelegramMessage
import canoe.models.{CallbackButtonSelected, Chat, Update}
import canoe.syntax._
import cats.syntax.all._
import cats.{Applicative, Monad, MonadError}
import constants._
import domain.callback.{CallbackAnswer, CallbackQueryExt}
import domain.message.ReplyMessage
import fs2.Pipe
import io.circe.generic.auto._
import logger.LogHandler
import util.DecodingUtil.decodeCallbackData

object MarshallingUtil {

  //TODO check why if i send 2 messages with little interval server crushed
  def scenario[F[_]: TelegramClient: Monad, A <: TelegramMessage](
    expect: Expect[A]
  )(
    func: A => Scenario[F, Unit]
  )(
    implicit logHandler: LogHandler[F]
  ): Scenario[F, Unit] = {
    val res = for {
      msg <- Scenario.expect(expect)
//      res <- Scenario.eval(UnpinChatMessage(msg.chat.id).call)
//      _   <- Scenario.eval(logHandler.info(s"Message unpinned - ${res}"))
      res <- Scenario.eval(PinChatMessage(msg.chat.id, msg.messageId, true.some).call)
      _   <- Scenario.eval(logHandler.info(s"Message pinned - ${res}"))
      _ <- func(msg).handleErrorWith(e => {
        for {
          _ <- Scenario.eval(logHandler.error(e.getMessage))
          _ <- Scenario.eval(msg.delete)
          _ <- Scenario.eval(msg.chat.send(e.getMessage, keyboard = mainMenuKeyboard))
        } yield ()
      })
    } yield ()

    res.handleErrorWith(e => Scenario.eval(logHandler.error(e.getMessage)))
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
    implicit me: MonadError[F, Throwable],
    logHandler:  LogHandler[F]
  ): Pipe[F, Update, Update] = { u =>
    u.evalTap {
      case CallbackButtonSelected(_, query) =>
        val handlers = answers
          .reduce(_ orElse _)
          .orElse(defaultAnswer)
        val res = for {
          answer <- me.fromEither(decodeCallbackData[CallbackAnswer](query))
          ext     = CallbackQueryExt(query, answer)
          _      <- handlers(ext)
        } yield ()
        res.handleErrorWith(e => {
          for {
            _ <- logHandler.error(e.getMessage)
            _ <- query.alert(e.getMessage)
          } yield ()
        })
      case _ => Applicative[F].unit
    }
  }
}
