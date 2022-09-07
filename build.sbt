ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

ThisBuild / organization := "student_bot_corp"

val doobieVersion      = "1.0.0-RC2"
val circeVersion       = "0.14.1"
val circeConfigVersion = "0.8.0"
val enumeratumVersion  = "1.7.1"
val redisVersion       = "1.2.0"
val loggerVersion      = "2.18.0"
val log4CatsVersion    = "2.4.0"
val slf4jVersion       = "1.7.36"
val logbackVersion     = "1.2.11"

libraryDependencies ++= Seq(
  "org.tpolecat"  %% "doobie-core"          % doobieVersion,
  "org.tpolecat"  %% "doobie-postgres"      % doobieVersion,
  "org.tpolecat"  %% "doobie-hikari"        % doobieVersion,
  "io.circe"      %% "circe-core"           % circeVersion,
  "io.circe"      %% "circe-generic"        % circeVersion,
  "io.circe"      %% "circe-generic-extras" % circeVersion,
  "io.circe"      %% "circe-parser"         % circeVersion,
  "io.circe"      %% "circe-config"         % circeConfigVersion,
  "com.beachape"  %% "enumeratum-doobie"    % enumeratumVersion,
  "org.typelevel" %% "log4cats-core"        % log4CatsVersion, // Only if you want to Support Any Backend
  "org.typelevel" %% "log4cats-slf4j"       % log4CatsVersion,
  "org.slf4j"      % "slf4j-api"            % slf4jVersion,
  "ch.qos.logback" % "logback-classic"      % logbackVersion,
)

addCompilerPlugin(
  "org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full
)

scalacOptions ++= Seq(
  "-Ymacro-annotations"
)

Compile / unmanagedJars += file("lib/canoe-assembly-0.1.0-SNAPSHOT.jar")

lazy val root = Project(
  id   = "student_bot",
  base = file("."),
)

Compile / herokuAppName := "scala-student-bot"

Compile / herokuJdkVersion := "11"

Compile / herokuFatJar := Some((assembly / assemblyOutputPath).value)

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

Compile / herokuProcessTypes := Map(
  "worker" -> ("java -jar target/scala-2.13/" + name.value + "-assembly-" + version.value + ".jar")
)
