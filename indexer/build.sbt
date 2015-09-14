name := """repo-indexer"""

version := "1.0"

scalaVersion := "2.11.7"

net.virtualvoid.sbt.graph.Plugin.graphSettings

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11",
  "net.ceedubs" %% "ficus" % "1.1.2",
  "com.jsuereth" %% "scala-arm" % "1.4",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
  "com.sksamuel.elastic4s" %% "elastic4s-core" % "1.7.4",
  "com.typesafe" % "config" % "1.3.0",
  "org.json4s" %% "json4s-jackson" % "3.2.11",
  "org.apache.logging.log4j" % "log4j-api" % "2.3",
  "org.apache.logging.log4j" % "log4j-core" % "2.3",
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.3",
  "org.apache.logging.log4j" % "log4j-jul" % "2.3",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.0.1.201506240215-r",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11" % "test",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
).map(_.exclude("commons-logging", "commons-logging"))

test in assembly := {}
