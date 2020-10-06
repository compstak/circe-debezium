package compstak.circe.debezium

import cats.implicits._
import io.circe.{Decoder, Encoder, Json, JsonObject}
import io.circe.syntax._
import compstak.circe.debezium.DebeziumOp.Create
import compstak.circe.debezium.DebeziumOp.Read
import compstak.circe.debezium.DebeziumOp.Delete
import compstak.circe.debezium.DebeziumOp.Update

case class DebeziumValue2[+A, +B](schema: JsonObject, payload: DebeziumPayload2[A, B])

object DebeziumValue2 {
  implicit def decoder[A: Decoder, B: Decoder]: Decoder[DebeziumValue2[A, B]] =
    Decoder.forProduct2("schema", "payload")(DebeziumValue2.apply[A, B])

  implicit def encoder[A: Encoder, B: Encoder]: Encoder[DebeziumValue2[A, B]] =
    Encoder.forProduct2("schema", "payload")(de => (de.schema, de.payload))
}

sealed trait DebeziumPayload2[+A, +B] {
  val before: Option[A]
  val after: Option[B]
  val source: Json
  val op: DebeziumOp
  val tsMs: Long
}

object DebeziumPayload2 {
  case class UpdatePayload[+A, +B](predecessor: A, successor: B, source: Json, tsMs: Long)
      extends DebeziumPayload2[A, B] {
    val before = Some(predecessor)
    val after = Some(successor)
    val op = DebeziumOp.Update
  }
  case class DeletePayload[+A](deleted: A, source: Json, tsMs: Long) extends DebeziumPayload2[A, Nothing] {
    val before = Some(deleted)
    val after = None
    val op = DebeziumOp.Delete
  }
  case class CreatePayload[+A](inserted: A, source: Json, tsMs: Long) extends DebeziumPayload2[Nothing, A] {
    val before = None
    val after = Some(inserted)
    val op = DebeziumOp.Create
  }
  case class InitialPayload[+A](inserted: A, source: Json, tsMs: Long) extends DebeziumPayload2[Nothing, A] {
    val before = None
    val after = Some(inserted)
    val op = DebeziumOp.Read
  }

  def apply[A, B](
    before: Option[A],
    after: Option[B],
    source: Json,
    op: DebeziumOp,
    tsMs: Long
  ): Either[String, DebeziumPayload2[A, B]] =
    op match {
      case DebeziumOp.Create => after.toRight("Missing 'after' in CreatePayload").map(CreatePayload(_, source, tsMs))
      case DebeziumOp.Read   => after.toRight("Missing 'after' in InitialPayload").map(InitialPayload(_, source, tsMs))
      case DebeziumOp.Delete => before.toRight("Missing 'before' in DeletePayload").map(DeletePayload(_, source, tsMs))
      case DebeziumOp.Update =>
        before.toRight("Missing 'before' in UpdatePayload").flatMap { b =>
          after.toRight("Missing 'after' in UpdatePayload").map(UpdatePayload(b, _, source, tsMs))
        }
    }

  implicit def decoder[A: Decoder, B: Decoder]: Decoder[DebeziumPayload2[A, B]] =
    c =>
      for {
        op <- c.downField("op").as[DebeziumOp]
        source <- c.downField("source").as[Json]
        tsMs <- c.downField("ts_ms").as[Long]
        result <- op match {
          case Create => c.downField("after").as[B].map(CreatePayload(_, source, tsMs))
          case Read   => c.downField("after").as[B].map(InitialPayload(_, source, tsMs))
          case Delete => c.downField("before").as[A].map(DeletePayload(_, source, tsMs))
          case Update =>
            c.downField("before")
              .as[A]
              .flatMap(before => c.downField("after").as[B].map(UpdatePayload(before, _, source, tsMs)))
        }
      } yield result

  implicit def encoder[A: Encoder, B: Encoder]: Encoder[DebeziumPayload2[A, B]] =
    Encoder.forProduct5("before", "after", "source", "op", "ts_ms")(de =>
      (de.before, de.after, de.source, de.op, de.tsMs)
    )
}
