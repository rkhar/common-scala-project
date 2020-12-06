

lazy val akkaVersion                     = "2.6.8"
lazy val akkaPersistenceCassandraVersion = "1.0.0-RC1"
lazy val akkaHttpVersion                 = "10.1.12"
lazy val enumeratumVersion               = "1.5.15"
lazy val enumeratumCirceVersion          = "1.5.23"
lazy val jodaVersion                     = "2.10.5"
lazy val swaggerVersion                  = "2.1.2"
lazy val json4sVersion                   = "3.6.7"
lazy val elastic4sVersion                = "7.3.5"

lazy val `root` = project
  .in(file("."))
  .settings(
    organization := "kz.dar.fintel",
    name := "main-api",
    version := "1.0.0",
    scalaVersion := "2.13.1",
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
//    resolvers += sys.env
//      .getOrElse("IVY2_REALM", "Artifactory Realm")
//      .at("https://artifactory.dar-dev.zone/artifactory/sbt-virtual/"),
    scalacOptions in Compile ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint"),
    javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed"  % akkaVersion,
      "com.typesafe.akka" %% "akka-stream"       % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j"        % akkaVersion,
      "com.typesafe.akka" %% "akka-http"         % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream-kafka" % "2.0.4",
      //
      "com.pauldijou"     %% "jwt-core"             % "4.2.0",
      "ch.qos.logback"    % "logback-classic"       % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
      "com.beachape"      %% "enumeratum"           % enumeratumVersion,
      "com.beachape"      %% "enumeratum-circe"     % enumeratumCirceVersion,
      "joda-time"         % "joda-time"             % jodaVersion,
      "org.joda"          % "joda-convert"          % "2.2.1",
      "io.scalaland"      %% "chimney"              % "0.5.0",
      "de.heikoseeberger" %% "akka-http-circe"      % "1.31.0",
      "io.circe"          %% "circe-core"           % "0.13.0",
      "io.circe"          %% "circe-generic"        % "0.13.0",
      "io.circe"          %% "circe-parser"         % "0.13.0",
      "io.circe"          %% "circe-literal"        % "0.13.0",
      "io.circe"          %% "circe-generic-extras" % "0.13.0",
      // swagger dependencies
      "javax.ws.rs"                  % "javax.ws.rs-api"           % "2.1.1",
      "com.github.swagger-akka-http" %% "swagger-akka-http"        % "2.0.5",
      "com.github.swagger-akka-http" %% "swagger-scala-module"     % "2.1.0",
      "io.swagger.core.v3"           % "swagger-core"              % swaggerVersion,
      "io.swagger.core.v3"           % "swagger-annotations"       % swaggerVersion,
      "io.swagger.core.v3"           % "swagger-models"            % swaggerVersion,
      "io.swagger.core.v3"           % "swagger-jaxrs2"            % swaggerVersion,
      "ch.megard"                    %% "akka-http-cors"           % "0.4.1",
      "kz.dar.eco"                   %% "dar-eco-exceptions"       % "3.0.4",
      "org.json4s"                   %% "json4s-jackson"           % json4sVersion,
      "org.json4s"                   %% "json4s-native"            % json4sVersion,
      "com.typesafe.akka"            %% "akka-actor-testkit-typed" % akkaVersion % Test,
      "org.scalatest"                %% "scalatest"                % "3.1.0" % Test,
      "commons-io"                   % "commons-io"                % "2.4" % Test,
      // redis scala
      "net.debasishg" %% "redisclient" % "3.30",
      //
      "com.sksamuel.elastic4s" %% "elastic4s-core"          % elastic4sVersion,
      "com.sksamuel.elastic4s" %% "elastic4s-json-circe"    % elastic4sVersion,
      "com.sksamuel.elastic4s" %% "elastic4s-client-esjava" % elastic4sVersion
    ),
    fork in run := false,
    packageName in Universal := "app",
    assemblyJarName in assembly := "app.jar",
    assemblyOutputPath in assembly := file("target/app.jar"),
    Global / cancelable := false, // ctrl-c
    mainClass in (Compile, run) := Some("kz.dar.fintel.main.api.Boot"),
    // disable parallel tests
    parallelExecution in Test := false,
    // show full stack traces and test case durations
    testOptions in Test += Tests.Argument("-oDF"),
    logBuffered in Test := false,
    licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
  )

packageName in Universal := "app"
enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)