package implicits

import io.circe.syntax._
import io.circe.{Decoder, Encoder}

import java.util.Date

object circe {
  implicit val dateTimeEncoder: Encoder[Date] = Encoder.instance(a => a.getTime.asJson)
  implicit val dateTimeDecoder: Decoder[Date] = Decoder.instance(a => a.as[Long].map(new Date(_)))
}
