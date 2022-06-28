package error.impl

import error.BotError

object redis {
  final case object RedisDataNotFound extends BotError {
    override def getMessage: String = "Данные в Redis не были найдены. Возможно запрос устарел, попробуйте снова."
  }
}
