package logger

import cats.Applicative
import cats.syntax.all._

trait LogHandler[F[_]] {
  def info(message:  String): F[Unit]
  def debug(message: String): F[Unit]
  def error(message: String): F[Unit]
}

object LogHandler {
  def of[F[_]: Applicative](infoF: String => Unit, debugF: String => Unit, errorF: String => Unit): LogHandler[F] = {
    new LogHandler[F] {
      override def info(message: String): F[Unit] = infoF(message).pure[F]

      override def debug(message: String): F[Unit] = debugF(message).pure[F]

      override def error(message: String): F[Unit] = errorF(message).pure[F]
    }
  }
}
