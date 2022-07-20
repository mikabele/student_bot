package app

import app.DbHandler._
import canoe.api.TelegramClient
import canoe.models.Update
import cats.MonadError
import cats.effect.{Async, Resource}
import cats.syntax.all._
import core.Bot
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.MkRedis
import domain.app._
import fs2.Stream
import logger.LogHandler
import repository.{QueueRepository, StudentRepository}
import scenarios.{AuthScenarios, QueueScenarios}
import service.{QueueService, StudentService}

//TODO remove Redis
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
      authService       = StudentService.of(studentRepository)(implicitly[MonadError[F, Throwable]])
      authScenario      = AuthScenarios.of(redis, authService)

      queueRepository = QueueRepository.of(tx)
      queueService   <- Resource.eval(QueueService.of(studentRepository, queueRepository))
      queueScenario   = QueueScenarios.of(redis, queueService, authService)

    } yield Bot
      .polling[F]
      .follow(
        authScenario.startBotScenario,
        queueScenario.addToQueueScenario,
        queueScenario.addFriendToQueue,
        queueScenario.getQueueSeries,
        queueScenario.getQueue,
        queueScenario.removeFromQueue
      )
  }
}
