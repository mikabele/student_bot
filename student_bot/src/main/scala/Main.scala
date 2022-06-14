import app.AppContext
import canoe.api.TelegramClient
import canoe.models.Update
import cats.Monad
import cats.effect.{Async, IO, IOApp, Resource}
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
      .flatMap(b => b)
      .compile
      .drain
      .recover { case e => e.printStackTrace() }
  }

  def botResource[F[_]: Async: Monad]: Resource[F, Stream[F, Update]] = {
    for {
      conf   <- Resource.eval(parser.decodePathF[F, AppConf]("app"))
      client <- TelegramClient[F](conf.tg.token)
      bot    <- AppContext.setUp[F](conf)(Async[F], client)
    } yield bot

  }
}
