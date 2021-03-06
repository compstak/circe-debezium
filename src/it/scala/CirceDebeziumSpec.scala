package compstak.circe.debezium

import scala.concurrent.duration._
import scala.util.Random

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.testing.scalatest.scalacheck.EffectCheckerAsserting

import cats.effect.{IO, Sync}

import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.{CheckerAsserting, ScalaCheckPropertyChecks}

import org.testcontainers.containers.Network
import com.dimafeng.testcontainers.{ ForAllTestContainer, GenericContainer, KafkaContainer, MultipleContainers, PostgreSQLContainer }
import GenericContainer.DockerImage

import doobie._
import doobie.implicits._
import cats._
import cats.data._
import cats.effect._
import cats.implicits._

import org.scalacheck._
import org.scalacheck.ScalacheckShapeless._
import org.scalacheck.cats.implicits._

import org.flywaydb.core.Flyway

import org.http4s._, Method._, client.Client, client.asynchttpclient.AsyncHttpClient, client.dsl.io._
import org.http4s.circe.CirceEntityCodec._

import io.circe._, literal._
import io.circe.parser._

import fs2.kafka._

class CirceDebeziumSpec
  extends AsyncIOSpec
  with Matchers
  with BeforeAndAfterAll
  with ScalaCheckPropertyChecks
  with ForAllTestContainer
  with scalatest.IOChecker {

  
  case class Debezium(field1: Int, field2: Short, field3: Long, field4: Boolean, field5: String, field6: Float, field7: Double, field8: Option[Int])

  implicit val dbDecoder = Decoder.forProduct8("field1", "field2", "field3", "field4", "field5", "field6", "field7", "field8")(Debezium.apply)

  implicit val intKeyDeserializer = Deserializer.lift { bytes =>
    IO.fromEither(decode[DebeziumKey[Int]](new String(bytes, "UTF8")))
  }

  implicit val dbvValueDeserializer = Deserializer.lift { bytes =>
    IO.fromEither(decode[DebeziumValue[Debezium]](new String(bytes, "UTF8")))
  }

  implicit val cs    = IO.contextShift(scala.concurrent.ExecutionContext.global)
  implicit val timer = IO.timer(scala.concurrent.ExecutionContext.global)

  lazy val kafka = KafkaContainer().configure { c => {
    c.withNetwork(Network.builder().build())
    ()
  }}

  lazy val kafkaUrl = kafka.networkAliases(0) + ":9092"

  val pgUser = "postgres"
  val pgPW   = "postgres"
  val pgPort = 5432

  lazy val postgres = PostgreSQLContainer(
    dockerImageNameOverride = "debezium/postgres:11",
    username                = pgUser,
    password                = pgPW
  ).configure { c => {
    c.withNetwork(kafka.network)
    ()
  }}

  lazy val jdbcUrl = "jdbc:postgresql://" + postgres.containerIpAddress + ":" + postgres.mappedPort(pgPort) + "/"

  val debeziumPort = 8083

  lazy val debezium = GenericContainer(
    DockerImage(Left("debezium/connect:1.2")),
    List(debeziumPort),
    Map(
      "BOOTSTRAP_SERVERS"    -> kafkaUrl,
      "CONFIG_STORAGE_TOPIC" -> "config.store",
      "OFFSET_STORAGE_TOPIC" -> "offset.store"
    )
  ).configure { c =>
    val _ = c.withNetwork(kafka.network)                  // Kafka Connect needs to be able to see Kafka
  }

  lazy val debeziumUri = Uri.unsafeFromString("http://" + debezium.containerIpAddress + ":" + debezium.mappedPort(debeziumPort))

  lazy val container = MultipleContainers(postgres, kafka, debezium)

  lazy val flyway = Flyway.configure().dataSource(jdbcUrl, pgUser, pgPW).load()

  // A transactor that gets connections from java.sql.DriverManager and executes blocking operations
  // on an our synchronous EC. See the chapter on connection handling for more info.
  lazy val transactor = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",   // driver classname
    jdbcUrl,                   // connect URL (driver-specific)
    pgUser,                // user
    pgPW,                // password
    Blocker.liftExecutionContext(ExecutionContexts.synchronous) // just for testing
  )

  var client: Client[IO] = _
  var close:  IO[Unit]   = _

  override def beforeAll(): Unit = {
    val _              = flyway.migrate()
    
    val (async, thunk) = (insertMany(genDebeziums((1000 to 2000).toList).sample.get).transact(transactor) *>
                          AsyncHttpClient.allocate[IO]()).unsafeRunSync
    client             = async
    close              = thunk
  }

  override protected def afterAll(): Unit = {
    close.unsafeRunSync()
  }

  def genDebeziums(field1s: List[Int]): Gen[List[Debezium]] = {
    field1s.traverse { field1 => for {
      field2 <- Arbitrary.arbitrary[Short]
      field3 <- Arbitrary.arbitrary[Long]
      field4 <- Arbitrary.arbitrary[Boolean]
      field5 <- Gen.identifier
      field6 <- Arbitrary.arbitrary[Float]
      field7 <- Arbitrary.arbitrary[Double]
      field8 <- Arbitrary.arbitrary[Option[Int]]
    } yield Debezium(field1, field2, field3, field4, field5, field6, field7, field8) }
  }

  def insertMany(dl: List[Debezium]): ConnectionIO[Int] = {
    val sql = "INSERT INTO debezium (field1, field2, field3, field4, field5, field6, field7, field8) values (?, ?, ?, ?, ?, ?, ?, ?);"
    Update[Debezium](sql).updateMany(dl)
  }

  def updateMany(dl: List[Debezium]): ConnectionIO[Int] = {
    dl.traverse { d =>
      Update[(Short, Long, Boolean, String, Float, Double, Option[Int], Int)]("UPDATE debezium SET field2 = ?, field3 = ?, field4 = ?, field5 = ?, field6 = ?, field7 = ?, field8 = ? WHERE field1 = ?;").run((d.field2, d.field3, d.field4, d.field5, d.field6, d.field7, d.field8, d.field1))
    }.map(_.sum)
  }

  val genDoobieProg: Gen[IO[Int]] = for {
    count   <- Gen.choose(1, 100)
    field1s <- Gen.sequence[List[Int], Int]((1 to count).map(Gen.const))
    ins     <- genDebeziums(field1s)
    perm     = Random.shuffle(field1s)
    indices <- Gen.delay(perm.take(Random.nextInt(count) + 1))
    ups     <- genDebeziums(indices)
    insert  <- Gen.delay(insertMany(ins))
    update  <- Gen.delay(updateMany(ups))
    delete  <- Gen.delay(sql"DELETE FROM debezium;".update.run)
  } yield (insert.transact(transactor), update.transact(transactor), delete.transact(transactor)).mapN((a, b, c) => a + b + c)

  val connectorName = "debezium-postgresql"
  val dbServerName  = "debezium"

  lazy val config = json"""{
    "name": $connectorName,
    "config": {
      "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
      "database.hostname": ${postgres.networkAliases(0)},
      "database.port": ${pgPort.toString},
      "database.user": $pgUser,
      "database.password": $pgPW,
      "database.server.id": "184054",
      "database.server.name": $dbServerName,
      "database.dbname": $pgUser,
      "table.whitelist": "public.debezium",
      "tasks.max": "1",
      "database.history.kafka.bootstrap.servers": $kafkaUrl,
      "database.history.kafka.topic": "dbhistory.debezium",
      "database.history.skip.unparseable.ddl": "true",
      "include.schema.changes": "true"
    }
  }"""

  lazy val consumerSettings = ConsumerSettings[IO, DebeziumKey[Int], Option[DebeziumValue[Debezium]]]
    .withAllowAutoCreateTopics(false)
    .withAutoOffsetReset(AutoOffsetReset.Earliest)
    .withBootstrapServers(kafka.bootstrapServers)
    .withGroupId("group")

  "Interacting with Debezium" - {
    "DebeziumPayload constructs correctly from real Debezium JSON" in {
      for {
        _ <- client.expect[Json](POST(config, debeziumUri / "connectors"))
        _ <- IO.sleep(30.seconds)
        _ <- forAll (genDoobieProg) { doobie => for {
          _ <- doobie // genDoobieProg.sample.get
          l <- consumerStream[IO]
            .using(consumerSettings)
            .evalTap(
              _.subscribeTo(s"${dbServerName + ".public.debezium"}")
            )
            .flatMap(_.stream)
            .evalTap(c => if (c.record.value.isEmpty) c.offset.commit else IO.unit)
            .mapFilter(c => c.record.value)
            .map(_.payload)
            .interruptAfter(2.minutes)
            .compile.toList
          _ <- IO(println(s"""*** LIST OF PAYLOADS: $l ***"""))
        } yield () }
      } yield succeed
    }
  }

  implicit def ioCheckingAsserting[A]: CheckerAsserting[IO[A]] { type Result = IO[Unit] } =
    new EffectCheckerAsserting
}
