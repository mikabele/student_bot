package util

import canoe.api.models.Keyboard
import canoe.api.{callbackQueryApi, chatApi, TelegramClient}
import canoe.models.CallbackQuery
import canoe.models.messages.{TelegramMessage, TextMessage}
import canoe.models.outgoing.MessageContent
import canoe.syntax._
import cats.Monad
import cats.syntax.all._
import constants._
import core._
import domain.message.ReplyMessage
import error.BotError
import logger.LogHandler
import syntax.syntax.{callback, Expect}
import util.bundle.ResourceBundleUtil

import java.util.ResourceBundle

object MarshallingUtil {

  //TODO check why if i send 2 messages with little interval server crushed
  //TODO change messagable signature

  def getBundleInScenario(msg: TelegramMessage, resourceBundleUtil: ResourceBundleUtil): ResourceBundle = msg match {
    case m: TextMessage => resourceBundleUtil.getBundle(m.from.get.languageCode.getOrElse(DEFAULT_LANG))
    case _ => resourceBundleUtil.getBundle(DEFAULT_LANG)
  }

  def scenario[F[_]: TelegramClient: Monad, A <: TelegramMessage](
    expect:             Expect[A],
    resourceBundleUtil: ResourceBundleUtil
  )(
    func: A => Scenario[F, Unit]
  )(
    implicit logHandler: LogHandler[F]
  ): Scenario[F, Unit] = {
    val res = for {
      msg   <- Scenario.expect(expect)
      bundle = getBundleInScenario(msg, resourceBundleUtil)
      _ <- func(msg)
        .handleErrorWith {
          case e: BotError => {
            for {
              _ <- Scenario.eval(logHandler.error(e.resourceString(bundle)))
              _ <- Scenario.eval(msg.chat.send(e.resourceString(bundle), keyboard = mainMenuKeyboard(bundle)))
            } yield ()
          }
          case ex: Throwable =>
            for {
              _ <- Scenario.eval(logHandler.error(ex.getMessage))
              // _ <- Scenario.eval(msg.chat.send(ex.getMessage(), keyboard = mainMenuKeyboard(bundle)))
            } yield ()
        }
    } yield ()

    res.handleErrorWith(_ => Scenario.done)
  }

  def defaultMsgAnswer[F[_]: TelegramClient: Monad, M](msg: TelegramMessage)(content: ReplyMessage[M]): F[Option[M]] = {

    for {
      res <- msg.chat.send(content.content, content.replyToMessageId, content.keyboard, content.disableNotification)
    } yield res.some

  }

  def defaultCallbackAnswer[F[_]: Monad: TelegramClient, M](
    query: CallbackQuery
  )(
    replyMessage: ReplyMessage[M]
  ): F[Option[M]] = {
    for {
      msg <- query.message.traverse(msg => defaultMsgAnswer(msg)(replyMessage))
      _   <- query.finish
    } yield msg.flatten
  }

  def sendMessage[F[_]: Monad: TelegramClient, M](
    answerFunc: ReplyMessage[M] => F[Option[M]],
    msgContent: MessageContent[M],
    keyboard:   Keyboard
  ): Scenario[F, Option[M]] = {
    val rm = ReplyMessage(msgContent, keyboard = keyboard)
    Scenario.eval(answerFunc(rm))
  }

  def sendMessageWithCallback[F[_]: Monad: TelegramClient, M <: TelegramMessage](
    answerFunc: ReplyMessage[M] => F[Option[M]],
    msgContent: MessageContent[M],
    keyboard:   Keyboard
  ): Scenario[F, CallbackQuery] = {

    for {
      msg   <- sendMessage(answerFunc, msgContent, keyboard)
      query <- Scenario.expect(callback(msg))
    } yield query
  }
}
