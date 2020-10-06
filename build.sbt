val scala212 = "2.12.10"
val scala213 = "2.13.2"
val supportedScalaVersions = List(scala212, scala213)

inThisBuild(
  List(
    scalaVersion := scala213,
    scalafmtOnCompile := true,
    organization := "com.compstak",
    homepage := Some(url("https://github.com/compstak/circe-debezium")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "LukaJCB",
        "Luka Jacobowitz",
        "luka.jacobowitz@gmail.com",
        url("https://github.com/LukaJCB")
      )
    )
  )
)

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  "-Ywarn-value-discard",
  "-Ywarn-dead-code",
  "-Xlint:infer-any",
  "-Xlint:nullary-override",
  "-Xlint:nullary-unit",
  "-Xfatal-warnings"
)

addCommandAlias("fmtAll", ";scalafmt; test:scalafmt; scalafmtSbt")
addCommandAlias("fmtCheck", ";scalafmtCheck; test:scalafmtCheck; scalafmtSbtCheck")

lazy val commonSettings = Seq(
  crossScalaVersions := supportedScalaVersions,
  addCompilerPlugin(("org.typelevel" %% "kind-projector" % "0.11.0").cross(CrossVersion.full)),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
)

lazy val publishSettings = Seq(
  publishArtifact in Test := false,
  pomIncludeRepository := { _ =>
    false
  }
)

val circeV = "0.13.0"
val tcsV = "0.37.0"
val doobieV = "0.9.0"
val http4sV = "0.21.6"

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    Defaults.itSettings,
    name := "circe-debezium",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeV,
      "io.circe" %% "circe-parser" % circeV % "test, it",
      "io.circe" %% "circe-literal" % circeV % "test, it",
      "ch.qos.logback" % "logback-classic" % "1.2.3" % "runtime",
      "org.scalatest" %% "scalatest" % "3.1.2" % "test, it",
      "org.tpolecat" %% "doobie-postgres" % doobieV % "it",
      "org.flywaydb" % "flyway-core" % "6.4.2" % "it",
      "org.tpolecat" %% "doobie-scalatest" % doobieV % "it",
      "com.codecommit" %% "cats-effect-testing-scalatest-scalacheck" % "0.4.0" % "it",
      "com.github.alexarchambault" %% "scalacheck-shapeless_1.14" % "1.2.5" % "it",
      "io.chrisdavenport" %% "cats-scalacheck" % "0.3.0" % "it",
      "com.dimafeng" %% "testcontainers-scala-scalatest" % tcsV % "it",
      "com.dimafeng" %% "testcontainers-scala-postgresql" % tcsV % "it",
      "com.dimafeng" %% "testcontainers-scala-kafka" % tcsV % "it",
      "org.http4s" %% "http4s-async-http-client" % http4sV % "it",
      "org.http4s" %% "http4s-circe" % http4sV % "it",
      "com.github.fd4s" %% "fs2-kafka" % "1.0.0" % "it"
    )
  )
