val scala212 = "2.12.10"
val scala213 = "2.13.2"
val supportedScalaVersions = List(scala212, scala213)

inThisBuild(
  List(
    scalaVersion := scala213,
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

val http4sVersion = "0.21.0-RC1"

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

val circeV = "0.12.3"

lazy val root = (project in file("."))
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    name := "circe-debezium",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeV,
      "io.circe" %% "circe-parser" % circeV % Test,
      "io.circe" %% "circe-literal" % circeV % Test,
      "org.scalatest" %% "scalatest" % "3.1.0" % Test
    )
  )
