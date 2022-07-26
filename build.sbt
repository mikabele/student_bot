ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

//TODO move to another project and deploy independently
lazy val bot = Project(id = "botAPI", base = file("botAPI_ext"))

lazy val root = Project(id = "root", base = file("."))
  .dependsOn(bot)

val tgApiVersion       = "0.6.0"
val doobieVersion      = "1.0.0-RC2"
val circeVersion       = "0.14.1"
val circeConfigVersion = "0.8.0"
val enumeratumVersion  = "1.7.0"
val redisVersion       = "1.2.0"
val loggerVersion      = "2.17.2"

libraryDependencies ++= Seq(
  "org.augustjune"          %% "canoe"                % tgApiVersion,
  "org.tpolecat"            %% "doobie-core"          % doobieVersion,
  "org.tpolecat"            %% "doobie-postgres"      % doobieVersion,
  "org.tpolecat"            %% "doobie-specs2"        % doobieVersion,
  "org.tpolecat"            %% "doobie-refined"       % doobieVersion,
  "org.tpolecat"            %% "doobie-hikari"        % doobieVersion,
  "org.flywaydb"             % "flyway-core"          % "8.5.12",
  "io.circe"                %% "circe-core"           % circeVersion,
  "io.circe"                %% "circe-generic"        % circeVersion,
  "io.circe"                %% "circe-generic-extras" % circeVersion,
  "io.circe"                %% "circe-optics"         % circeVersion,
  "io.circe"                %% "circe-parser"         % circeVersion,
  "io.circe"                %% "circe-config"         % circeConfigVersion,
  "io.circe"                %% "circe-core"           % circeVersion,
  "io.circe"                %% "circe-generic"        % circeVersion,
  "io.circe"                %% "circe-generic-extras" % circeVersion,
  "io.circe"                %% "circe-optics"         % circeVersion,
  "io.circe"                %% "circe-parser"         % circeVersion,
  "io.circe"                %% "circe-refined"        % circeVersion,
  "com.beachape"            %% "enumeratum-circe"     % enumeratumVersion,
  "dev.profunktor"          %% "redis4cats-effects"   % redisVersion,
  "org.apache.logging.log4j" % "log4j-api"            % loggerVersion,
  "org.apache.logging.log4j" % "log4j-core"           % loggerVersion,
  "org.slf4j"                % "slf4j-nop"            % "1.7.36",
)

addCompilerPlugin(
  "org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full
)

scalacOptions ++= Seq(
  "-Ymacro-annotations"
)
