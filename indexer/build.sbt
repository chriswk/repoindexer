name := """Repo Indexer"""

version := "1.0"

scalaVersion := "2.11.7"

lazy val root = (project in file(".")).
  enablePlugins(BuildInfoPlugin).
  settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "no.finntech",
    buildInfoObject := "BuildInfo"
  )

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "com.sksamuel.elastic4s" %% "elastic4s-core" % "1.7.0",
  "com.codacy" %% "stash-scala-client" % "1.0.0-beta4",
  "com.github.kxbmap" %% "configs" % "0.2.4",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.0.1.201506240215-r",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11" % "test",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
)
