package compstak.circe.debezium

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.testing.scalatest.scalacheck.EffectCheckerAsserting
import cats.effect.{IO, Sync}
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.{CheckerAsserting, ScalaCheckPropertyChecks}
import org.scalacheck.Gen
import org.scalacheck.ScalacheckShapeless._

import org.testcontainers.containers.Network
import com.dimafeng.testcontainers.{ ForAllTestContainer, GenericContainer, KafkaContainer, MultipleContainers, PostgreSQLContainer }
import GenericContainer.DockerImage

import doobie._
import doobie.implicits._
import cats._
import cats.data._
import cats.effect._
import cats.implicits._

import org.flywaydb.core.Flyway
import shapeless.Generic

class CirceDebeziumSpec
  extends AsyncIOSpec
  with Matchers
  with BeforeAndAfterAll
  with ScalaCheckPropertyChecks
  with ForAllTestContainer
  with scalatest.IOChecker {

  val pgUser = "postgres"
  val pgPW   = "postgres"

  lazy val postgres = PostgreSQLContainer(
    dockerImageNameOverride = "postgres:11",
    username                = pgUser,
    password                = pgPW
  )

  lazy val kafka = KafkaContainer().configure { c =>
    val _ = c.withNetwork(Network.builder().build())      // Getting the network without setting it is deprecated
  }

  lazy val debezium = GenericContainer(
    DockerImage(Left("debezium/connect:1.2")),
    List(8083),
    Map(
      "BOOTSTRAP_SERVERS"    -> (kafka.networkAliases(0) + ":9092"),
      "CONFIG_STORAGE_TOPIC" -> "config.store",
      "OFFSET_STORAGE_TOPIC" -> "offset.store"
    )
  ).configure { c =>
    val _ = c.withNetwork(kafka.network)                  // Kafka Connect needs to be able to see Kafka
  }

  lazy val container = MultipleContainers(postgres, kafka, debezium)

  lazy val jdbcUrl = "jdbc:postgresql://" + postgres.containerIpAddress + ":" + postgres.mappedPort(5432) + "/"

  lazy val flyway = Flyway.configure().dataSource(jdbcUrl, pgUser, pgPW).load()

  // We need a ContextShift[IO] before we can construct a Transactor[IO]. The passed ExecutionContext
  // is where nonblocking operations will be executed. For testing here we're using a synchronous EC.
  implicit val cs = IO.contextShift(ExecutionContexts.synchronous)

  // A transactor that gets connections from java.sql.DriverManager and executes blocking operations
  // on an our synchronous EC. See the chapter on connection handling for more info.
  lazy val transactor = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",   // driver classname
    jdbcUrl,                   // connect URL (driver-specific)
    pgUser,                // user
    pgPW,                // password
    Blocker.liftExecutionContext(ExecutionContexts.synchronous) // just for testing
  )

  override def beforeAll(): Unit = {
    val _ = flyway.migrate()
  }

  case class Person(id: Int, name: String)

  val startingId = 100

  val genPerson: Gen[Person] = for {
    id   <- Gen.choose(startingId, startingId + 100)
    name <- Gen.identifier
  } yield Person(id, name)

  val genSetPerson: Gen[Set[Person]] = Gen.containerOf[Set, Person](genPerson)

  def insertMany(ps: Set[Person]): ConnectionIO[Int] = {
    val sql = "insert into person (id, name) values (?, ?)"
    Update[Person](sql).updateMany(ps.toList)
  }

  "Interacting with PostgreSQL" - {

    val trivial = sql"SELECT id, name FROM person WHERE id = 1;".query[Person]

    "A trivial SELECT typechecks" in {
      check(trivial)
      succeed
    }

    "A trivial SELECT works" in {
      trivial.unique.transact(transactor).asserting(_ shouldBe Person(1, "Axel"))
    }

    "Inserting a bunch of Persons works" in {
      forAll (genSetPerson) { sp =>
        (
          insertMany(sp).transact(transactor) *>
          sql"SELECT id, name FROM person WHERE id >= $startingId;".query[Person].to[Set].transact(transactor) <*
          sql"DELETE FROM person WHERE id >= $startingId;".update.run.transact(transactor)
        )
        .asserting(_ shouldBe sp)
      }
    }

    implicit def ioCheckingAsserting[A]: CheckerAsserting[IO[A]] { type Result = IO[Unit] } =
      new EffectCheckerAsserting
  }
}
