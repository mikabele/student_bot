package logger

import cats.Applicative
import org.apache.logging.log4j.LogManager

package object impl {
  def log4jLogHandler[F[_]: Applicative](layerName: String): LogHandler[F] = {
    val logger = LogManager.getLogger(layerName)
    LogHandler.of(
      logger.info,
      logger.debug,
      logger.error
    )
  }

//  def dummy[F[_]: Applicative]: LogHandler[F] = LogHandler.of(

//  )
}
