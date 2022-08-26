import app.AppContext
import canoe.api.TelegramClient
import canoe.models.Update
import cats.MonadError
import cats.effect.{Async, IO, IOApp, Resource}
import domain.app._
import fs2.Stream
import io.circe.config.parser
import io.circe.generic.auto._
import org.http4s.blaze.client.BlazeClientBuilder

import scala.concurrent.duration.Duration
object Main extends IOApp.Simple {

  def run: IO[Unit] = {
    Stream
      .resource(
        botResource[IO]
      )
      .flatten
      .compile
      .drain
  }

  def botResource[F[_]: Async: MonadError[*[_], Throwable]]: Resource[F, Stream[F, Update]] = {
    for {
      conf       <- Resource.eval(parser.decodePathF[F, AppConf]("app"))
      httpClient <- BlazeClientBuilder[F].withIdleTimeout(Duration.Inf).withRequestTimeout(Duration.Inf).resource
      tgClient    = TelegramClient.fromHttp4sClient(conf.tg.token)(httpClient)
      bot        <- AppContext.setUp[F](conf)(Async[F], tgClient, implicitly[MonadError[F, Throwable]])
    } yield bot

  }
}
