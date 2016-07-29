name := """repo-indexer"""

version := "1.0"

scalaVersion := "2.11.8"

val akkaVersion = "2.4.8"
val elastic4sVersion = "2.3.1"
val log4j2Version = "2.6.2"

net.virtualvoid.sbt.graph.Plugin.graphSettings

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-core" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-jackson-experimental" % akkaVersion,
  "org.reactivestreams" % "reactive-streams" % "1.0.0",
  "net.ceedubs" %% "ficus" % "1.1.2",
  "com.lihaoyi" %% "ammonite-ops" % "0.4.8",
  "com.jsuereth" %% "scala-arm" % "1.4",
  "com.sksamuel.elastic4s" %% "elastic4s-core" % elastic4sVersion,
  "com.sksamuel.elastic4s" %% "elastic4s-streams" % elastic4sVersion,
  "com.typesafe" % "config" % "1.3.0",
  "org.json4s" %% "json4s-jackson" % "3.2.11",
  "org.apache.logging.log4j" % "log4j-api" % log4j2Version,
  "org.apache.logging.log4j" % "log4j-core" % log4j2Version,
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4j2Version,
  "org.apache.logging.log4j" % "log4j-jul" % log4j2Version,
  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.4.1.201607150455-r",
  "com.github.javaparser" % "javaparser-core" % "2.3.0",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % "test",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
).map(_.exclude("commons-logging", "commons-logging"))

initialCommands :=
  """
    |import akka.actor.ActorSystem
    |import akka.stream.ActorMaterializer
    |import akka.stream.scaladsl._
    |import no.finn.repoindexer.flows.Cloner
    |import no.finn.repoindexer.flows.Stash
    |import no.finn.repoindexer.flows.Indexing
    |import no.finn.repoindexer._
    |import com.sksamuel.elastic4s.streams.RequestBuilder
    |import com.sksamuel.elastic4s.{ElasticsearchClientUri, BulkCompatibleDefinition, ElasticClient, ElasticDsl}
    |import java.io.File
    |import scala.concurrent.ExecutionContext.Implicits.global
    |import com.sksamuel.elastic4s.streams.ReactiveElastic._
    |import scala.util.{Success, Failure}
    |
    |
    |implicit lazy val system = ActorSystem("RepoIndexer")
    |implicit val materializer = ActorMaterializer()
    |def stripSemiColon(s: String): String = s.replaceAll(";", "")
    |  val summer = Sink.fold[Int, Int](0)(_+_)

  """.stripMargin
