package core

import canoe.api.TelegramClient
import canoe.api.sources.Hook
import canoe.models.{InputFile, Update}
import cats.effect.{Async, Concurrent, Deferred, Ref, Resource, Temporal}
import conversions.pipes
import fs2.{Pipe, Stream}
import cats.syntax.all._

import scala.concurrent.duration.FiniteDuration

class Bot[F[_]: Concurrent](val updates: Stream[F, Update]) {

  /** Defines the behavior of the bot.
    *
    * models.MyBot is reacting to the incoming messages following provided scenarios.
    * When the user input is not matching/stops matching particular scenario
    * it means that current interaction is not described with this scenario
    * and bot will not continue acting it out.
    *
    * @example {{{
    *   val scenario = for {
    *     chat <- Scenario.expect(command("first").chat)
    *     _    <- Scenario.eval(chat.send("first message received"))
    *     _    <- Scenario.expect(command("second"))
    *     _    <- Scenario.eval(chat.send("second message received"))
    *   }
    *
    *  user > /first
    *  bot > first message received
    *  user > something else
    *  *end of the scenario*
    *
    *  user > /first
    *  bot > first message received
    *  user > /second
    *  bot > second message received
    *  *end of the scenario*
    * }}}
    *
    * Each scenario is handled concurrently across all chats,
    * which means that scenario cannot be blocked by any other scenario being in progress.
    *
    * All the behavior is suspended as an effect of resulting stream, without changing its elements.
    * Also, result stream is not halted by the execution of any particular scenario.
    *
    * @return Stream of all updates which your bot receives from Telegram service
    */
  def follow(scenarios: Scenario[F, Unit]*): Stream[F, Update] = {
    def runScenarios(updates: Broadcast[F, Update]): Stream[F, Nothing] =
      updates
        .subscribe(1)
        .through(pipes.messageables)
        .map(m => {
          Stream.emits(scenarios).map(sc => fork(updates, m).through(sc.pipe)).parJoinUnbounded.drain
        })
        .parJoinUnbounded

    def fork(updates: Broadcast[F, Update], m: Messageable): Stream[F, Messageable] =
      updates
        .subscribe(1)
        .through(filterMessageables(m))
        .through(debounce)
        .cons1(m)

    def filterMessageables(msg: Messageable): Pipe[F, Update, Messageable] =
      _.through(pipes.messageables)
        .filter(m => { m.getMessage.flatMap(msgNO => msg.getMessage.map(_.chat.id == msgNO.chat.id)).getOrElse(false) })

    def debounce[F[_]: Concurrent, A]: Pipe[F, A, A] =
      input =>
        Stream.eval(Ref[F].of[Option[Deferred[F, A]]](None)).flatMap { ref =>
          val hook = Stream
            .repeatEval(Deferred[F, A])
            .evalMap(df => ref.set(Some(df)) *> df.get)

          val update = input.evalMap { a =>
            ref.getAndSet(None).flatMap(_.traverse_(_.complete(a)))
          }

          hook.concurrently(update)
        }

    Stream.eval(Broadcast[F, Update]).flatMap { topic =>
      val pop = updates.evalTap(topic.publish1)
      val run = runScenarios(topic)
      pop.concurrently(run)
    }
  }
}

object Bot {
  def fromStream[F[_]: Concurrent](updates: Stream[F, Update]): Bot[F] = new Bot(updates)

  /** Creates a bot which receives incoming updates using long polling mechanism.
    *
    * See [[https://en.wikipedia.org/wiki/Push_technology#Long_polling wiki]].
    */
  def polling[F[_]: Concurrent: TelegramClient]: Bot[F] =
    new Bot[F](Polling.continual)

  /** Creates a bot which receives incoming updates using long polling mechanism
    * with custom polling interval.
    *
    * See [[https://en.wikipedia.org/wiki/Push_technology#Long_polling wiki]].
    */
  def polling[F[_]: Temporal: TelegramClient](interval: FiniteDuration): Bot[F] =
    new Bot[F](Polling.metered(interval))

  /** Creates a bot which receives incoming updates by setting a webhook.
    * After the bot is used, the webhook is deleted even in case of interruptions or errors.
    *
    * @param url         HTTPS url to which updates will be sent
    * @param host        Network interface to bind the server
    * @param port        Port which will be used for listening for the incoming updates.
    *                    Default is 8443.
    * @param certificate Public key of self-signed certificate (including BEGIN and END portions)
    */
  def hook[F[_]: TelegramClient: Async](
    url:         String,
    host:        String            = "0.0.0.0",
    port:        Int               = 8443,
    certificate: Option[InputFile] = None
  ): Resource[F, Bot[F]] =
    Hook.install(url, host, port, certificate).map(h => new Bot(h.updates))

}
