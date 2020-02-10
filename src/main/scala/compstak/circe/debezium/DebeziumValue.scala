package compstak.circe.debezium

import cats.implicits._
import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax._

case class DebeziumValue[A](schema: Json, payload: DebeziumPayload[A])

object DebeziumValue {
  implicit def decoder[A: Decoder]: Decoder[DebeziumValue[A]] =
    Decoder.forProduct2("schema", "payload")(DebeziumValue.apply[A])

  implicit def encoder[A: Encoder]: Encoder[DebeziumValue[A]] =
    Encoder.forProduct2("schema", "payload")(de => (de.schema, de.payload))
}

sealed trait DebeziumPayload[A] {
  val before: Option[A]
  val after: Option[A]
  val source: Json
  val op: DebeziumOp
  val tsMs: Long
}
object DebeziumPayload {
  case class UpdatePayload[A](predecessor: A, successor: A, source: Json, tsMs: Long) extends DebeziumPayload[A] {
    val before = Some(predecessor)
    val after = Some(successor)
    val op = DebeziumOp.Update
  }
  case class DeletePayload[A](deleted: A, source: Json, tsMs: Long) extends DebeziumPayload[A] {
    val before = Some(deleted)
    val after = None
    val op = DebeziumOp.Delete
  }
  case class CreatePayload[A](inserted: A, source: Json, tsMs: Long) extends DebeziumPayload[A] {
    val before = None
    val after = Some(inserted)
    val op = DebeziumOp.Create
  }

  def apply[A](
    before: Option[A],
    after: Option[A],
    source: Json,
    op: DebeziumOp,
    tsMs: Long
  ): Option[DebeziumPayload[A]] =
    op match {
      case DebeziumOp.Create => after.map(CreatePayload(_, source, tsMs))
      case DebeziumOp.Delete => before.map(DeletePayload(_, source, tsMs))
      case DebeziumOp.Update => (before, after).mapN(UpdatePayload(_, _, source, tsMs))
    }

  implicit def decoder[A: Decoder]: Decoder[DebeziumPayload[A]] =
    Decoder
      .forProduct5("before", "after", "source", "op", "ts_ms")(DebeziumPayload.apply[A])
      .emap(_.toRight("Not a valid payload"))
  implicit def encoder[A: Encoder]: Encoder[DebeziumPayload[A]] =
    Encoder.forProduct5("before", "after", "source", "op", "ts_ms")(de =>
      (de.before, de.after, de.source, de.op, de.tsMs)
    )
}
