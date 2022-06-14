package scenarios

import canoe.api.{Scenario, TelegramClient}
import canoe.models._
import canoe.syntax._
import cats.Monad
import cats.syntax.all._
import scenarios.impl.AuthScenarioImpl
import service.AuthService

trait AuthScenario[F[_]] {
  def startBot: Scenario[F, Unit]

  def getStudentsAnswer: PartialFunction[CallbackQuery, F[Unit]]

  def getGroupsAnswer: PartialFunction[CallbackQuery, F[Unit]]

  def answers: Seq[PartialFunction[CallbackQuery, F[Unit]]]
}

object AuthScenario {
  def of[F[_]: TelegramClient: Monad](authService: AuthService[F]): AuthScenario[F] = {
    new AuthScenarioImpl[F](authService)
  }
}
