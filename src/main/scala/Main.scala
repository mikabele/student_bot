import app.AppContext
import canoe.api.TelegramClient
import canoe.models.Update
import cats.MonadError
import cats.effect.{Async, IO, IOApp, Resource}
//import cats.implicits._
import cats.syntax.all._
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
      client <- TelegramClient[F](conf.tg.token)
      bot    <- AppContext.setUp[F](conf)(Async[F], client, implicitly[MonadError[F, Throwable]])
    } yield bot

  }
}
