package scenarios

import canoe.api.{Scenario, TelegramClient}
import cats.MonadError
import org.typelevel.log4cats.Logger
import scenarios.impl.AuthScenariosImpl
import service.StudentService
import util.bundle.ResourceBundleUtil

trait AuthScenarios[F[_]] {
  def startBotScenario: Scenario[F, Unit]

  def signOutScenario: Scenario[F, Unit]
}

object AuthScenarios {
  def of[F[_]: TelegramClient: MonadError[*[_], Throwable]: Logger](
    studentService: StudentService[F],
    bundleUtil:     ResourceBundleUtil
  ): AuthScenarios[F] = {
    new AuthScenariosImpl[F](studentService, bundleUtil)
  }
}
