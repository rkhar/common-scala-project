logLevel := Level.Info

credentials   += Credentials(Path.userHome / ".ivy2" / ".credentials")

resolvers += Classpaths.sbtPluginReleases
resolvers += Classpaths.typesafeReleases

//addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.10")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.2.7")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.19")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.4.0")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.0.6")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.8.2"
