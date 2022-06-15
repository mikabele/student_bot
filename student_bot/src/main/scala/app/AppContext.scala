package app

import app.DbHandler._
import canoe.api.{Bot, TelegramClient}
import canoe.models.Update
import cats.effect.{Async, Resource}
import cats.syntax.all._
import domain.app._
import fs2.Stream
import repository.AuthRepository
import scenarios.AuthScenario
import service.AuthService
import util.AnswerCallbacksUtil

object AppContext {
  def setUp[F[_]: Async: TelegramClient](conf: AppConf): Resource[F, Stream[F, Update]] = {
    for {
      tx <- transactor[F](conf.db)

      migrator <- Resource.eval(migrator[F](conf.db))
      _        <- Resource.eval(migrator.migrate())

      authRepository = AuthRepository.of(tx)
      authService    = AuthService.of(authRepository)
      authScenario   = AuthScenario.of(authService)
      authAnswers    = authScenario.answers

      answers = AnswerCallbacksUtil.answerCallback(authAnswers)
    } yield Bot
      .polling[F]
      .follow(authScenario.startBot)
      .through(answers)
  }
}
