package app

import app.DbHandler._
import canoe.api.{Bot, TelegramClient}
import canoe.models.Update
import cats.MonadError
import cats.effect.{Async, Resource}
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.MkRedis
import domain.app._
import fs2.Stream
import logger.LogHandler
import repository.{QueueRepository, StudentRepository}
import scenarios.{AuthScenarios, QueueScenarios}
import service.{AuthService, QueueService}
import util.MarshallingUtil

object AppContext {
  def setUp[F[_]: Async: TelegramClient: MkRedis: MonadError[*[_], Throwable]](
    conf: AppConf
  ): Resource[F, Stream[F, Update]] = {
    implicit val logHandler: LogHandler[F] = logger.impl.log4jLogHandler("root")
    for {
      tx <- transactor[F](conf.db)

      migrator <- Resource.eval(migrator[F](conf.db))
      _        <- Resource.eval(migrator.migrate())

      redis <- Redis[F].utf8(conf.redis.uri)

      studentRepository = StudentRepository.of(tx)
      authService       = AuthService.of(studentRepository)(implicitly[MonadError[F, Throwable]])
      authScenario      = AuthScenarios.of(redis, authService)
      authAnswers       = authScenario.answers

      queueRepository = QueueRepository.of(tx)
      queueService   <- Resource.eval(QueueService.of(studentRepository, queueRepository))
      queueScenario  <- Resource.eval(QueueScenarios.of(redis, queueService, authService))
      queueAnswers    = queueScenario.answers

      answers = MarshallingUtil.answerCallback(authAnswers ++ queueAnswers)
    } yield Bot
      .polling[F] //.updates.through(pipes.inlineQueries)
      .follow(
        authScenario.startBotScenario,
        queueScenario.addToQueueScenario,
        queueScenario.addFriendToQueue,
        queueScenario.getQueueSeries,
        queueScenario.getQueue
      )
      .through(answers)
  }
}
