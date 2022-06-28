import app.AppContext
import canoe.api.TelegramClient
import canoe.models.Update
import cats.MonadError
import cats.effect.{Async, IO, IOApp, Resource}
import dev.profunktor.redis4cats.effect.MkRedis
//import cats.implicits._
import cats.syntax.all._
import dev.profunktor.redis4cats.effect.Log.Stdout.instance
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
      .handleErrorWith(error => IO.pure(println(error.getMessage)))
  }

  def botResource[F[_]: Async: MkRedis: MonadError[*[_], Throwable]]: Resource[F, Stream[F, Update]] = {
    for {
      conf   <- Resource.eval(parser.decodePathF[F, AppConf]("app"))
      client <- TelegramClient[F](conf.tg.token)
      bot    <- AppContext.setUp[F](conf)(Async[F], client, MkRedis[F], implicitly[MonadError[F, Throwable]])
    } yield bot

  }
}
