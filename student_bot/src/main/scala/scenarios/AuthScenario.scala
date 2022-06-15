package scenarios

import canoe.api.{Scenario, TelegramClient}
import canoe.models._
import canoe.syntax._
import cats.Monad
import cats.syntax.all._
import scenarios.impl.AuthScenarioImpl
import service.AuthService

trait AuthScenario[F[_]] extends CallbackAnswerHandler[F] {
  def startBot: Scenario[F, Unit]
}

object AuthScenario {
  def of[F[_]: TelegramClient: Monad](authService: AuthService[F]): AuthScenario[F] = {
    new AuthScenarioImpl[F](authService)
  }
}
