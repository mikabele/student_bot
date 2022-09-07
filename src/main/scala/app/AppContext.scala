package app

import app.DbHandler._
import canoe.api.{Bot, TelegramClient}
import canoe.models.Update
import cats.MonadError
import cats.effect.{Async, Resource}
import domain.app._
import fs2.Stream
import org.typelevel.log4cats._
import org.typelevel.log4cats.slf4j._
import repository.{QueueRepository, StudentRepository}
import scenarios.{AdminScenarios, AuthScenarios, QueueScenarios}
import service.{QueueService, StudentService}
import util.bundle.ResourceBundleUtil

object AppContext {
  def setUp[F[_]: Async: TelegramClient: MonadError[*[_], Throwable]](
    conf: AppConf
  ): Resource[F, Stream[F, Update]] = {
    implicit val logger: Logger[F] = Slf4jLogger.getLogger[F]
    for {
      tx <- transactor[F](conf.db)

      bundleUtil = ResourceBundleUtil.of(conf.bundle.path, conf.bundle.languages)

      studentRepository = StudentRepository.of(tx)
      authService       = StudentService.of(studentRepository)
      authScenario      = AuthScenarios.of(authService, bundleUtil)

      queueRepository = QueueRepository.of(tx)
      queueService   <- Resource.eval(QueueService.of(studentRepository, queueRepository))
      queueScenario   = QueueScenarios.of(queueService, authService, bundleUtil)

      adminScenarios = AdminScenarios.of(authService, queueService, bundleUtil)

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
        adminScenarios.addGroupScenario,
        adminScenarios.initAdminMenuScenario,
        adminScenarios.addQueuesScenario
      )
  }
}
