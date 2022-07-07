package scenarios

import canoe.api.{Scenario, TelegramClient}
import cats.MonadError
import dev.profunktor.redis4cats.RedisCommands
import logger.LogHandler
import scenarios.impl.AuthScenariosImpl
import service.AuthService

trait AuthScenarios[F[_]] extends CallbackAnswerHandler[F] {
  def startBotScenario: Scenario[F, Unit]

  def signOutScenario: Scenario[F, Unit]
}

object AuthScenarios {
  def of[F[_]: TelegramClient: MonadError[*[_], Throwable]: LogHandler](
    redisCommands: RedisCommands[F, String, String],
    authService:   AuthService[F]
  ): AuthScenarios[F] = {
    new AuthScenariosImpl[F](redisCommands, authService)
  }
}
