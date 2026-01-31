ThisBuild / scalaVersion := "3.3.4"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .configs(IntegrationTest)
  .settings(
    name := "hello1",
    Defaults.itSettings,
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % "0.23.30",
      "org.http4s" %% "http4s-dsl" % "0.23.30",
      "org.http4s" %% "http4s-circe" % "0.23.30",
      "io.circe" %% "circe-generic" % "0.14.10",
      "org.typelevel" %% "munit-cats-effect" % "2.0.0" % "test,it",
      "org.http4s" %% "http4s-ember-client" % "0.23.30" % IntegrationTest,
      "org.http4s" %% "http4s-circe" % "0.23.30" % "test,it",
      "io.circe" %% "circe-generic" % "0.14.10" % "test,it",
    ),
    Docker / packageName := "hello1",
    dockerBaseImage := "eclipse-temurin:21-jre",
    dockerExposedPorts := Seq(8080),
  )

addCommandAlias("intTests", "IntegrationTest/test")
