ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

lazy val root = (project in file("."))
  .settings(
    name := "student_bot"
  )

val tgApiVersion       = "0.6.0"
val doobieVersion      = "1.0.0-RC2"
val circeVersion       = "0.14.1"
val circeConfigVersion = "0.8.0"

libraryDependencies ++= Seq(
  "org.augustjune" %% "canoe"           % tgApiVersion,
  "org.tpolecat"   %% "doobie-core"     % doobieVersion,
  "org.tpolecat"   %% "doobie-postgres" % doobieVersion,
  "org.tpolecat"   %% "doobie-specs2"   % doobieVersion,
  "org.tpolecat"   %% "doobie-refined"  % doobieVersion,
  "org.tpolecat"   %% "doobie-hikari"   % doobieVersion,
  "org.flywaydb"    % "flyway-core"     % "8.5.12",
//  "com.softwaremill.sttp.client3" %% "async-http-client-backend-cats" % "3.6.2",
//  "org.typelevel"                 %% "cats-effect"                    % "3.3.12"
  "io.circe" %% "circe-core"           % circeVersion,
  "io.circe" %% "circe-generic"        % circeVersion,
  "io.circe" %% "circe-generic-extras" % circeVersion,
  "io.circe" %% "circe-optics"         % circeVersion,
  "io.circe" %% "circe-parser"         % circeVersion,
  "io.circe" %% "circe-config"         % circeConfigVersion,
  "io.circe" %% "circe-core"           % circeVersion,
  "io.circe" %% "circe-generic"        % circeVersion,
  "io.circe" %% "circe-generic-extras" % circeVersion,
  "io.circe" %% "circe-optics"         % circeVersion,
  "io.circe" %% "circe-parser"         % circeVersion,
  "io.circe" %% "circe-refined"        % circeVersion,
)
