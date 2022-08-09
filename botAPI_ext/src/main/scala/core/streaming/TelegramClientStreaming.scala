package core.streaming

import canoe.api.TelegramClient
import fs2.Stream

trait TelegramClientStreaming[F[_]] extends TelegramClient[F]{
  def downloadFile(pathToFile: String): Stream[F, Byte]
}
