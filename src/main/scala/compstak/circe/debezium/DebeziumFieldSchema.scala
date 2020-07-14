package compstak.circe.debezium

import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import cats.implicits._

sealed abstract class DebeziumSchemaPrimitive(val tag: String) extends Product with Serializable

object DebeziumSchemaPrimitive {
  final case object Int32 extends DebeziumSchemaPrimitive("int32")
  final case object Int16 extends DebeziumSchemaPrimitive("int16")
  final case object Int64 extends DebeziumSchemaPrimitive("int64")
  final case object String extends DebeziumSchemaPrimitive("string")
  final case object Float32 extends DebeziumSchemaPrimitive("float32")
  final case object Float64 extends DebeziumSchemaPrimitive("float64")
  final case object Boolean extends DebeziumSchemaPrimitive("boolean")

  val all = List(Int32, Int16, Int64, String, Float32, Float64, Boolean)

  def fromString(s: String): Option[DebeziumSchemaPrimitive] = all.find(_.tag === s)

  implicit val decoder: Decoder[DebeziumSchemaPrimitive] =
    Decoder.decodeString.emap(s => all.find(_.tag === s).liftTo[Either[String, ?]](s"Invalid schema type: $s"))

  implicit val encoder: Encoder[DebeziumSchemaPrimitive] =
    Encoder.instance(_.tag.asJson)
}

final case class DebeziumFieldSchema(`type`: DebeziumSchemaPrimitive, optional: Boolean, field: String)

object DebeziumFieldSchema {
  implicit val encoder: Encoder[DebeziumFieldSchema] =
    Encoder.forProduct3("type", "optional", "field")(dfs => (dfs.`type`, dfs.optional, dfs.field))

  implicit val decoder: Decoder[DebeziumFieldSchema] =
    Decoder.forProduct3("type", "optional", "field")(DebeziumFieldSchema.apply)
}
