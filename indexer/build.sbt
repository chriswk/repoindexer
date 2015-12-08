name := """repo-indexer"""

version := "1.0"

scalaVersion := "2.11.7"

val akkaVersion = "2.4.1"
val elastic4sVersion = "2.1.0"

net.virtualvoid.sbt.graph.Plugin.graphSettings

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-experimental" % "2.0-M2",
  "com.typesafe.akka" %% "akka-http-experimental" % "2.0-M2",
  "com.typesafe.akka" %% "akka-http-core-experimental" % "2.0-M2",
  "com.typesafe.akka" %% "akka-http-jackson-experimental" % "2.0-M2",
  "org.reactivestreams" % "reactive-streams" % "1.0.0",
  "net.ceedubs" %% "ficus" % "1.1.2",
  "com.lihaoyi" %% "ammonite-ops" % "0.4.8",
  "com.jsuereth" %% "scala-arm" % "1.4",
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
  "com.typesafe.akka" %% "akka-stream-testkit-experimental" % "2.0-M1" % "test",
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
    |
    |
    |implicit lazy val system = ActorSystem("RepoIndexer")
    |implicit val materializer = ActorMaterializer()
    |def stripSemiColon(s: String): String = s.replaceAll(";", "")
    |implicit val indexDocumentBuilder = new RequestBuilder[IndexCandidate] {
    |    import ElasticDsl._
    |    def request(doc: IndexCandidate):BulkCompatibleDefinition = index into "sources" / "file" fields (
    |      "slug" -> doc.slug,
    |      "project" -> doc.project,
    |      "filename" -> doc.path.last,
    |      "extension" -> doc.path.ext,
    |      "imports" -> doc.imports.map { i =>
    |        i.getName.getName
    |      },
    |      "fullyQualifiedImport" -> doc.imports.map { i =>
    |        stripSemiColon(i.toString)
    |      },
    |      "package" -> doc.packageName.map(p => p.getName.getName).getOrElse(""),
    |      "packageName" -> doc.packageName.map(p => p.getName).getOrElse(""),
    |      "fullyQualifiedPackage" -> doc.packageName,
    |      "types" -> doc.typeDeclarations.map { tDecl =>
    |        stripSemiColon(tDecl.toString)
    |      },
    |      "content" -> doc.content
    |    )
    |  }
    |  val summer = Sink.fold[Int, Int](0)(_+_)

  """.stripMargin
