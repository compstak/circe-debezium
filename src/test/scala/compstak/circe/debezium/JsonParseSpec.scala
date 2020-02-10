package compstak.circe.debezium

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.literal._
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import java.time.Instant

class JsonParseSpec extends AnyFlatSpec with Matchers {
  it should "parse a debezium string value" in {

    val json = json"""
      {
        "schema": {},
        "payload": {
          "before": null,
          "after": "example",
          "source": {
            "version": "0.9.5.Final",
            "connector": "mysql",
            "name": "example",
            "server_id": 0,
            "ts_sec": 0,
            "gtid": null,
            "file": "mysql-bin-changelog.003734",
            "pos": 411,
            "row": 0,
            "snapshot": true,
            "thread": null,
            "db": "compstak",
            "table": "example",
            "query": null
          },
          "op": "c",
          "ts_ms": 1580935308320
        }
      }
    """

    val decoded = Decoder[DebeziumValue[String]].decodeJson(json)
    decoded.isRight shouldBe true

    decoded.toOption.get.payload shouldBe a[DebeziumPayload.CreatePayload[_]]
  }
}
