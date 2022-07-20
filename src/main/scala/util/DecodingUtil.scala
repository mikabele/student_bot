package util

import canoe.models.CallbackQuery
import cats.syntax.all._
import error.BotError
import error.impl.json.DecodingError
import error.impl.redis.RedisDataNotFound
import io.circe.Decoder
import io.circe.parser.decode

object DecodingUtil {
  def decodeCallbackData[M: Decoder](query: CallbackQuery): Either[BotError, M] = {
    query.data match {
      case Some(value) => decodeExt(value)
      case _           => DecodingError.asLeft
    }
  }

  private def decodeExt[M: Decoder](value: String): Either[BotError, M] =
    decode[M](value) match {
      case Right(res) => res.asRight[BotError]
      case _          => DecodingError.asLeft
    }

  def parseRedisData[M: Decoder](data: Option[String]): Either[BotError, M] = {
    data match {
      case Some(value) => decodeExt(value)
      case None        => RedisDataNotFound.asLeft[M]
    }
  }
}
