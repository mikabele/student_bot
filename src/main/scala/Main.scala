import app.AppContext
import canoe.models.Update
import cats.MonadError
import cats.effect.{Async, IO, IOApp, Resource}
import core.streaming.Http4sTelegramClientExt
import domain.app._
import fs2.Stream
import io.circe.config.parser
import io.circe.generic.auto._

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
      conf   <- Resource.eval(parser.decodePathF[F, AppConf]("app"))
//      httpClient <- BlazeClientBuilder[F].resource
//      tgClient = TelegramClient.fromHttp4sClient[F](conf.tg.token)(httpClient)
      tgClient <- Http4sTelegramClientExt.of(conf.tg.token)
      bot    <- AppContext.setUp[F](conf)(Async[F], tgClient, implicitly[MonadError[F, Throwable]])
    } yield bot

  }
}
