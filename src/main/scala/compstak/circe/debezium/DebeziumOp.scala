package compstak.circe.debezium

import cats.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.syntax._

sealed abstract class DebeziumOp(val tag: String)
object DebeziumOp {
  case object Create extends DebeziumOp("c")
  case object Update extends DebeziumOp("u")
  case object Delete extends DebeziumOp("d")

  def fromString(s: String): Option[DebeziumOp] =
    Set(Create, Update, Delete).find(_.tag === s)

  implicit val encoder: Encoder[DebeziumOp] = _.tag.asJson
  implicit val decoder: Decoder[DebeziumOp] =
    Decoder.decodeString.emap(s => fromString(s).toRight("Could not decode a valid DebeziumOp"))
}
