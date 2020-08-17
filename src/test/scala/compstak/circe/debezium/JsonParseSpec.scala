package compstak.circe.debezium

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.literal._
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import java.time.Instant
import io.circe.JsonObject

class JsonParseSpec extends AnyFlatSpec with Matchers {

  it should "parse a debezium key from the documentation" in {

    val payload = DebeziumKeyPayload.simple(1, "id")
    val json = json"""
      {
        "schema": {
          "type": "struct",
          "fields": [{
            "type": "int32",
            "optional": false,
            "field": "id"
          }],
          "optional": false,
          "name": "experiment.public.atable.Key"
        },
        "payload": {
          "id": 1
        }
      }
    """

    val decoded = Decoder[DebeziumKey[Int]].decodeJson(json)

    decoded.isRight shouldBe true

    val key = decoded.toOption.get
    key.payload shouldBe payload
    key.schema.fields.length shouldBe 1
    key.schema.fields.headOption.get.optional shouldBe false

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

    decoded.toOption.get.payload shouldBe a[DebeziumPayload2.CreatePayload[_]]
  }

  it should "parse a composite debezium key" in {

    case class AtableKey(id: Int, data: String)
    val key = AtableKey(43, "foo")

    implicit val encoderAtable: Encoder[AtableKey] = Encoder.forProduct2("id", "data")(a => (a.id, a.data))
    implicit val decoderAtable: Decoder[AtableKey] = Decoder.forProduct2("id", "data")(AtableKey.apply)

    val debeziumKey = DebeziumKey(
      DebeziumKeySchema(
        List(
          DebeziumFieldSchema(DebeziumSchemaPrimitive.Int32, false, "id"),
          DebeziumFieldSchema(DebeziumSchemaPrimitive.String, false, "data")
        ),
        "experiment.experiment.atable.Key"
      ),
      DebeziumKeyPayload.CompositeKeyPayload(key)
    )
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
          "data": "foo"
        }
      }"""

    val parsed = Decoder[DebeziumKey[AtableKey]].decodeJson(json)

    parsed.isRight shouldBe true

    val decoded = parsed.toOption.get
    decoded shouldEqual debeziumKey
    decoded.payload shouldBe a[DebeziumKeyPayload.CompositeKeyPayload[_]]

    debeziumKey.asJson shouldEqual json
  }
}
