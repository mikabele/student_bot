package scenarios

import canoe.api.{Scenario, TelegramClient}
import cats.Monad
import cats.effect.kernel.Concurrent
import cats.effect.std.Semaphore
import cats.syntax.all._
import dev.profunktor.redis4cats.RedisCommands
import logger.LogHandler
import scenarios.impl.QueueScenariosImpl
import service.{AuthService, QueueService}

trait QueueScenarios[F[_]] extends CallbackAnswerHandler[F] {
  def addToQueueScenario: Scenario[F, Unit]
  def getQueueSeries:     Scenario[F, Unit]
  def getQueue:           Scenario[F, Unit]
  def addFriendToQueue:   Scenario[F, Unit]
//  def takeAnotherPlace: Scenario[F, Unit]
//  def removeFromQueue:  Scenario[F, Unit]
}

object QueueScenarios {
  def of[F[_]: TelegramClient: Monad: Concurrent: LogHandler](
    redisCommands: RedisCommands[F, String, String],
    queueService:  QueueService[F],
    authService:   AuthService[F]
  ): F[QueueScenarios[F]] = {
    //TODO remove semaphores
    val keys = Seq("addToQueue", "getQueue")
    for {
      values    <- keys.traverse(_ => Semaphore[F](1))
      semaphores = Map.from(keys.zip(values))
    } yield new QueueScenariosImpl[F](redisCommands, queueService, authService)
  }
}
