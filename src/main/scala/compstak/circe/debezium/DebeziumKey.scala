package compstak.circe.debezium

import io.circe.{Decoder, Encoder, Json, JsonObject}
import io.circe.syntax._
import cats.implicits._

case class DebeziumKey[A](schema: JsonObject, payload: DebeziumKeyPayload[A])

object DebeziumKey {
  implicit def decoder[A: Decoder]: Decoder[DebeziumKey[A]] =
    Decoder.forProduct2("schema", "payload")(DebeziumKey.apply[A])

  implicit def encoder[A: Encoder]: Encoder[DebeziumKey[A]] =
    Encoder.forProduct2("schema", "payload")(k => (k.schema, k.payload))
}

case class DebeziumKeyPayload[A](id: A, idName: String)
object DebeziumKeyPayload {
  implicit def decoder[A: Decoder]: Decoder[DebeziumKeyPayload[A]] =
    Decoder.decodeJsonObject.emap { obj =>
      for {
        (key, value) <- obj.toMap.headOption.toRight("Invalid KeyPayload: Empty JsonObject")
        id <- value.as[A].leftMap(_.toString)
      } yield DebeziumKeyPayload(id, key)
    }

  implicit def encoder[A: Encoder]: Encoder[DebeziumKeyPayload[A]] = dp => Json.obj(dp.idName -> dp.id.asJson)
}
