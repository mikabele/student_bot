package scenarios

import canoe.api.TelegramClient
import cats.effect.Sync
import core.Scenario
import core.streaming.TelegramClientStreaming
import logger.LogHandler
import scenarios.impl.AdminScenariosImpl
import util.bundle.ResourceBundleUtil

trait AdminScenarios[F[_]] {
  def addGroupScenario: Scenario[F, Unit]

  def addQueuesScenario: Scenario[F, Unit]
}

object AdminScenarios {
  def of[F[_]: TelegramClient: TelegramClientStreaming: Sync: LogHandler](
    bundleUtil: ResourceBundleUtil
  ): AdminScenarios[F] = {
    new AdminScenariosImpl[F](bundleUtil)
  }
}
