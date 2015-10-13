name := """repo-indexer"""

version := "1.0"

scalaVersion := "2.11.7"

val akkaVersion = "2.4.0"
val elastic4sVersion = "1.7.4"

net.virtualvoid.sbt.graph.Plugin.graphSettings

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-experimental" % "1.0",
  "net.ceedubs" %% "ficus" % "1.1.2",
  "com.jsuereth" %% "scala-arm" % "1.4",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
  "com.sksamuel.elastic4s" %% "elastic4s-core" % elastic4sVersion,
  "com.sksamuel.elastic4s" %% "elastic4s-streams" % elastic4sVersion,
  "com.typesafe" % "config" % "1.3.0",
  "org.json4s" %% "json4s-jackson" % "3.2.11",
  "org.apache.logging.log4j" % "log4j-api" % "2.4",
  "org.apache.logging.log4j" % "log4j-core" % "2.4",
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.4",
  "org.apache.logging.log4j" % "log4j-jul" % "2.4",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.0.1.201506240215-r",
  "com.github.javaparser" % "javaparser-core" % "2.2.2",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
).map(_.exclude("commons-logging", "commons-logging"))

test in assembly := {}

initialCommands :=
  """
    |import java.io.{FileInputStream, File}
    |import com.github.javaparser.JavaParser
    |import com.github.javaparser.ast.CompilationUnit
    |import com.ning.http.client.RequestBuilder
    |import com.sksamuel.elastic4s.ElasticDsl.index
    |import com.typesafe.config.ConfigFactory
    |import net.ceedubs.ficus.Ficus._
    |
    |import akka.actor.ActorSystem
    |import akka.stream.ActorMaterializer
    |import akka.stream.scaladsl._
    |import com.jcraft.jsch.{Session, JSch}
    |import com.sksamuel.elastic4s.{ElasticsearchClientUri, ElasticClient}
    |import dispatch._
    |import org.apache.logging.log4j.{LogManager, Logger}
    |import org.eclipse.jgit.api.{TransportConfigCallback, Git}
    |import org.eclipse.jgit.internal.storage.file.FileRepository
    |import org.eclipse.jgit.lib.TextProgressMonitor
    |import org.eclipse.jgit.transport.{SshTransport, Transport, OpenSshConfig, JschConfigSessionFactory}
    |import org.eclipse.jgit.util.FS
    |import org.elasticsearch.common.settings.ImmutableSettings
    |import org.json4s.DefaultFormats
    |import resource._
    |import scala.annotation.tailrec
    |import scala.util.{Try, Success, Failure}
    |import org.json4s._
    |import org.json4s.jackson.JsonMethods._
    |import org.json4s.JsonDSL._
    |import scala.concurrent.ExecutionContext.Implicits.global
    |
    |implicit lazy val system = ActorSystem("RepoIndexer")
    |implicit val materializer = ActorMaterializer()
    |
    |val ctx = no.finn.repoindexer.ApplicationMain
  """.stripMargin
