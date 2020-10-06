package compstak.circe

import io.circe.Json

package object debezium {
  type DebeziumValue[A] = DebeziumValue2[A, A]
  type DebeziumValueState[A] = DebeziumValue2[Option[Json], A]
  type DebeziumPayload[A] = DebeziumPayload2[A, A]
}
