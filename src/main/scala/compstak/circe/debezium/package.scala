package compstak.circe

package object debezium {
  type DebeziumValue[A] = DebeziumValue2[A, A]
  type DebeziumPayload[A] = DebeziumPayload2[A, A]
}
