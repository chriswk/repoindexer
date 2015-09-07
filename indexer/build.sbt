name := """Repo Indexer"""

version := "1.0"

scalaVersion := "2.11.7"

lazy val root = (project in file(".")).
  enablePlugins(BuildInfoPlugin).settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "no.finntech",
    buildInfoObject := "BuildInfo"
  )
  net.virtualvoid.sbt.graph.Plugin.graphSettings

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
  "com.sksamuel.elastic4s" %% "elastic4s-core" % "1.7.0",
  "com.typesafe" % "config" % "1.3.0",
  "org.apache.logging.log4j" % "log4j-api" % "2.3",
  "org.apache.logging.log4j" % "log4j-core" % "2.3",
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.3",
  "org.apache.logging.log4j" % "log4j-jul" % "2.3",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.0.1.201506240215-r",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11" % "test",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
).map(_.exclude("commons-logging", "commons-logging"))

assemblyJarName in assembly := "repoindexer.jar"
