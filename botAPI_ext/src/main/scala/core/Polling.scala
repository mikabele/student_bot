package core

import canoe.api.TelegramClient
import canoe.methods.updates.GetUpdates
import canoe.models.Update
import canoe.syntax.methodOps
import cats.Functor
import cats.effect.Temporal
import cats.syntax.all._

import scala.concurrent.duration._
import scala.language.postfixOps
import fs2.Stream

/** models.Polling method of getting Telegram updates.
  *
  * @param timeout Determines how long the Telegram service will wait
  *                before it sends the response when there's no updates.
  *                Enables [[https://en.wikipedia.org/wiki/Push_technology#Long_polling long polling]].
  */
class Polling[F[_]: TelegramClient: Functor](timeout: FiniteDuration) {
  def pollUpdates(startOffset: Long): Stream[F, List[Update]] =
    Stream(()).repeat
      .covary[F]
      .evalMapAccumulate(startOffset) { case (offset, _) => requestUpdates(offset) }
      .map { case (_, updates) => updates }

  private def requestUpdates(offset: Long): F[(Long, List[Update])] =
    GetUpdates(offset = Some(offset), timeout = Some(timeout.toSeconds.toInt)).call
      .map(updates => (lastId(updates).map(_ + 1).getOrElse(offset), updates))

  private def lastId(updates: List[Update]): Option[Long] =
    updates match {
      case Nil      => None
      case nonEmpty => Some(nonEmpty.map(_.updateId).max)
    }
}

object Polling {

  /** Default timeout duration for long polling
    */
  private val longPollTimeout: FiniteDuration = 30 seconds

  /** Polls new batch of updates whenever consumer is ready
    */
  def continual[F[_]: TelegramClient: Functor]: Stream[F, Update] =
    new Polling[F](longPollTimeout).pollUpdates(0).flatMap(Stream.emits)

  /** Polls new batch of updates when consumer is ready and `interval` passed since the last polling
    */
  def metered[F[_]: TelegramClient: Temporal](
    interval: FiniteDuration
  ): Stream[F, Update] =
    new Polling[F](longPollTimeout).pollUpdates(0).metered(interval).flatMap(Stream.emits(_))
}
