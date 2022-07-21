package scenarios

import canoe.api.TelegramClient
import cats.Monad
import cats.effect.kernel.Concurrent
import cats.syntax.all._
import core.Scenario
import dev.profunktor.redis4cats.RedisCommands
import logger.LogHandler
import scenarios.impl.QueueScenariosImpl
import service.{QueueService, StudentService}

trait QueueScenarios[F[_]] /*extends CallbackAnswerHandler[F] */ {
  def addToQueueScenario: Scenario[F, Unit]
  def getQueueSeries:     Scenario[F, Unit]
  def getQueue:           Scenario[F, Unit]
  def addFriendToQueue:   Scenario[F, Unit]
//  def takeAnotherPlace: Scenario[F, Unit]
  def removeFromQueue: Scenario[F, Unit]
}

object QueueScenarios {
  def of[F[_]: TelegramClient: Monad: Concurrent: LogHandler](
    redisCommands: RedisCommands[F, String, String],
    queueService:  QueueService[F],
    authService:   StudentService[F]
  ): QueueScenarios[F] = {
    new QueueScenariosImpl[F](redisCommands, queueService, authService)
  }
}
