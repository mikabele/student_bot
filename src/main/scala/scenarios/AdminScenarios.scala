package scenarios

import canoe.api.{Scenario, TelegramClient}
import cats.Monad
import cats.effect.Sync
import org.typelevel.log4cats.Logger
import scenarios.impl.AdminScenariosImpl
import service.StudentService
import util.bundle.ResourceBundleUtil

trait AdminScenarios[F[_]] {
  def addGroupScenario: Scenario[F, Unit]

  def addQueuesScenario: Scenario[F, Unit]
}

object AdminScenarios {
  def of[F[_]: TelegramClient: Sync: Monad: Logger](
    studentService: StudentService[F],
    bundleUtil: ResourceBundleUtil
  ): AdminScenarios[F] = {
    new AdminScenariosImpl[F](studentService, bundleUtil)
  }
}
