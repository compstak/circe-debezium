package compstak.circe.debezium

import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax._

case class DebeziumKey[A](payload: DebeziumKeyPayload[A], schema: Json)

object DebeziumKey {
  implicit def decoder[A: Decoder]: Decoder[DebeziumKey[A]] =
    Decoder.forProduct2("payload", "schema")(DebeziumKey.apply[A])

  implicit def encoder[A: Encoder]: Encoder[DebeziumKey[A]] =
    Encoder.forProduct2("payload", "schema")(k => (k.payload, k.schema))
}

case class DebeziumKeyPayload[A](id: A)
object DebeziumKeyPayload {
  implicit def decoder[A: Decoder]: Decoder[DebeziumKeyPayload[A]] =
    Decoder.forProduct1("id")(DebeziumKeyPayload.apply[A])
  implicit def encoder[A: Encoder]: Encoder[DebeziumKeyPayload[A]] = dp => Json.obj("id" -> dp.id.asJson)
}
