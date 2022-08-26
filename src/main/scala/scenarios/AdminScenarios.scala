package scenarios

import canoe.api.Scenario

trait AdminScenarios[F[_]] {
  def addGroupScenario: Scenario[F, Unit]

  def addQueuesScenario: Scenario[F, Unit]
}

object AdminScenarios {
//  def of[F[_]: TelegramClient: Sync: LogHandler](
//    bundleUtil: ResourceBundleUtil
//  ): AdminScenarios[F] = {
//    new AdminScenariosImpl[F](bundleUtil)
//  }
}
