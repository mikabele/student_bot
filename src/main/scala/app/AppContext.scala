package app

import app.DbHandler._
import canoe.models.Update
import cats.MonadError
import cats.effect.{Async, Resource}
import core.Bot
import core.streaming.TelegramClientStreaming
import domain.app._
import fs2.Stream
import logger.LogHandler
import repository.{QueueRepository, StudentRepository}
import scenarios.{AdminScenarios, AuthScenarios, QueueScenarios}
import service.{QueueService, StudentService}
import util.bundle.ResourceBundleUtil

object AppContext {
  def setUp[F[_]: Async: TelegramClientStreaming: MonadError[*[_], Throwable]](
    conf: AppConf
  ): Resource[F, Stream[F, Update]] = {
    implicit val logHandler: LogHandler[F] = logger.impl.log4jLogHandler("root")
    for {
      tx <- transactor[F](conf.db)

      //TODO fix migrations on heroku
      //migrator <- Resource.eval(migrator[F](conf.db))
      //_        <- Resource.eval(migrator.migrate())

      bundleUtil = ResourceBundleUtil.of(conf.bundle.path, conf.bundle.languages)

      studentRepository = StudentRepository.of(tx)
      authService       = StudentService.of(studentRepository)(implicitly[MonadError[F, Throwable]])
      authScenario      = AuthScenarios.of(authService, bundleUtil)

      queueRepository = QueueRepository.of(tx)
      queueService   <- Resource.eval(QueueService.of(studentRepository, queueRepository))
      queueScenario   = QueueScenarios.of(queueService, authService, bundleUtil)

      adminScenarios = AdminScenarios.of(bundleUtil)

    } yield Bot
      .polling[F]
      .follow(
        authScenario.startBotScenario,
        authScenario.signOutScenario,
        queueScenario.addToQueueScenario,
        queueScenario.addFriendToQueueScenario,
        queueScenario.getQueueSeriesScenario,
        queueScenario.getQueueScenario,
        queueScenario.removeFromQueueScenario,
        queueScenario.takeAnotherPlaceScenario,
        adminScenarios.addGroupScenario
      )
  }
}
