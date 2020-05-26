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
  case class UpdatePayload[A](predecessor: Option[A], successor: Option[A], source: Json, tsMs: Long)
      extends DebeziumPayload[A] {
    val before = predecessor
    val after = successor
    val op = DebeziumOp.Update
  }
  case class DeletePayload[A](deleted: Option[A], source: Json, tsMs: Long) extends DebeziumPayload[A] {
    val before = deleted
    val after = None
    val op = DebeziumOp.Delete
  }
  case class CreatePayload[A](inserted: A, source: Json, tsMs: Long) extends DebeziumPayload[A] {
    val before = None
    val after = Some(inserted)
    val op = DebeziumOp.Create
  }
  case class InitialPayload[A](inserted: A, source: Json, tsMs: Long) extends DebeziumPayload[A] {
    val before = None
    val after = Some(inserted)
    val op = DebeziumOp.Read
  }

  def apply[A](
    before: Option[A],
    after: Option[A],
    source: Json,
    op: DebeziumOp,
    tsMs: Long
  ): Either[String, DebeziumPayload[A]] =
    op match {
      case DebeziumOp.Create => after.toRight("Missing 'after' in CreatePayload").map(CreatePayload(_, source, tsMs))
      case DebeziumOp.Read   => after.toRight("Missing 'after' in InitialPayload").map(InitialPayload(_, source, tsMs))
      case DebeziumOp.Delete => DeletePayload(before, source, tsMs).asRight
      case DebeziumOp.Update => UpdatePayload(before, after, source, tsMs).asRight
    }

  implicit def decoder[A: Decoder]: Decoder[DebeziumPayload[A]] =
    Decoder
      .forProduct5("before", "after", "source", "op", "ts_ms")(DebeziumPayload.apply[A])
      .emap(identity)

  implicit def encoder[A: Encoder]: Encoder[DebeziumPayload[A]] =
    Encoder.forProduct5("before", "after", "source", "op", "ts_ms")(de =>
      (de.before, de.after, de.source, de.op, de.tsMs)
    )
}
