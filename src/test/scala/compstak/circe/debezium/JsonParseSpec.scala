package compstak.circe.debezium

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.literal._
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import java.time.Instant

class JsonParseSpec extends AnyFlatSpec with Matchers {

  it should "parse a debezium key from the documentation" in {

    val payload = DebeziumKeyPayload(1, "id")
    val json = json"""
      {
        "schema": {
          "type": "struct",
          "name": "PostgreSQL_server.public.customers.Key",
          "optional": false,
          "fields": [
                {
                    "name": "id",
                    "index": "0",
                    "schema": {
                        "type": "INT32",
                        "optional": "false"
                    }
                }
            ]
        },
        "payload": {
            "id": "1"
        }
      }
    """

    val decoded = Decoder[DebeziumKey[Int]].decodeJson(json)

    decoded.isRight shouldBe true

    decoded.toOption.get.payload shouldBe payload

    payload.asJson.noSpaces shouldBe """{"id":1}"""
  }

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

  it should "parse a composite debezium key" in {
    val json = json"""
      {
        "schema": {
          "type": "struct",
          "fields": [{
            "type": "int32",
            "optional": false,
            "field": "id"
          }, {
            "type": "string",
            "optional": false,
            "field": "data"
          }],
          "optional": false,
          "name": "experiment.experiment.atable.Key"
        },
        "payload": {
          "id": 43,
          "data": "2020-07-08T14:15:14.441-04:00"
        }
      }"""
  }
}
