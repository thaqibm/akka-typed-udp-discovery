ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

val akkaVersion = "2.6.20"

lazy val root = (project in file("."))
  .settings(
      name := "akka-typed-udp-discovery",
      libraryDependencies ++= Seq(
          "com.typesafe.akka" %% "akka-actor" % akkaVersion,
          "com.typesafe.akka" %% "akka-remote" % akkaVersion,
          "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
          "com.typesafe.akka" %% "akka-cluster-metrics" % akkaVersion,
          "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
           "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
          "com.typesafe.akka" %% "akka-actor" % akkaVersion,
          "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
          "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion),
      libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.4.4",
      libraryDependencies += "ch.qos.logback" % "logback-core" % "1.4.4",
      libraryDependencies ++= Seq("org.slf4j" % "slf4j-api" % "2.0.3")
  )

