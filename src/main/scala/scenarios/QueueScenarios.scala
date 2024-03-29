package scenarios

import canoe.api.{Scenario, TelegramClient}
import cats.Monad
import cats.effect.kernel.Concurrent
import org.typelevel.log4cats.Logger
import scenarios.impl.QueueScenariosImpl
import service.{QueueService, StudentService}
import util.bundle.ResourceBundleUtil

trait QueueScenarios[F[_]] {
  def addToQueueScenario:       Scenario[F, Unit]
  def getQueueSeriesScenario:   Scenario[F, Unit]
  def getQueueScenario:         Scenario[F, Unit]
  def addFriendToQueueScenario: Scenario[F, Unit]
  def takeAnotherPlaceScenario: Scenario[F, Unit]
  def removeFromQueueScenario:  Scenario[F, Unit]
}

object QueueScenarios {
  def of[F[_]: TelegramClient: Monad: Concurrent: Logger](
                                                               queueService: QueueService[F],
                                                               studentService:  StudentService[F],
                                                               bundleUtil:   ResourceBundleUtil
  ): QueueScenarios[F] = {
    new QueueScenariosImpl[F](queueService, studentService, bundleUtil)
  }
}
