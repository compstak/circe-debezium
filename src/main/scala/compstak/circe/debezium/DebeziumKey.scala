package compstak.circe.debezium

import io.circe.{Decoder, Encoder, Json, JsonObject}
import io.circe.syntax._
import cats.implicits._
import io.circe.DecodingFailure

case class DebeziumKey[A](schema: DebeziumKeySchema, payload: DebeziumKeyPayload[A])

object DebeziumKey {
  implicit def decoder[A: Decoder]: Decoder[DebeziumKey[A]] =
    Decoder.forProduct2("schema", "payload")(DebeziumKey.apply[A])

  implicit def encoder[A: Encoder]: Encoder[DebeziumKey[A]] =
    Encoder.forProduct2("schema", "payload")(k => (k.schema, k.payload))
}

case class DebeziumKeySchema(fields: List[DebeziumFieldSchema], name: String)

object DebeziumKeySchema {
  implicit val encoder: Encoder[DebeziumKeySchema] =
    dks =>
      Json.obj(
        "type" -> "struct".asJson,
        "fields" -> dks.fields.asJson,
        "optional" -> false.asJson,
        "name" -> dks.name.asJson
      )

  implicit val decoder: Decoder[DebeziumKeySchema] =
    Decoder.forProduct2("fields", "name")(DebeziumKeySchema.apply)
}

sealed trait DebeziumKeyPayload[A] {
  def id: A
}

object DebeziumKeyPayload {

  def simple[A](id: A, idName: String): DebeziumKeyPayload[A] =
    SimpleKeyPayload(id, idName)

  final case class SimpleKeyPayload[A](id: A, idName: String) extends DebeziumKeyPayload[A]
  final case class CompositeKeyPayload[A](id: A) extends DebeziumKeyPayload[A]

  private def simpleDecoder[A: Decoder](obj: JsonObject): Decoder[DebeziumKeyPayload[A]] =
    c =>
      for {
        (key, value) <- obj.toMap.headOption.toRight(DecodingFailure("Invalid KeyPayload: Empty JsonObject", c.history))
        id <- value.as[A]
      } yield SimpleKeyPayload(id, key)

  private def compositeDecoder[A: Decoder]: Decoder[DebeziumKeyPayload[A]] =
    Decoder[A].map(CompositeKeyPayload(_))

  implicit def decoder[A: Decoder]: Decoder[DebeziumKeyPayload[A]] =
    Decoder.decodeJsonObject.flatMap { obj =>
      simpleDecoder[A](obj).or(compositeDecoder[A])
    }

  implicit def encoder[A: Encoder]: Encoder[DebeziumKeyPayload[A]] = {
    case SimpleKeyPayload(id, idName) => Json.obj(idName -> id.asJson)
    case CompositeKeyPayload(id)      => id.asJson
  }

}
