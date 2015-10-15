package no.finn.repoindexer.flows

import java.io.File
import java.nio.file.Paths

import akka.stream.io.Framing
import akka.util.ByteString
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.javaparser.JavaParser
import com.github.javaparser.ast.{Node, ImportDeclaration, CompilationUnit}
import com.sksamuel.elastic4s.streams.RequestBuilder
import com.sksamuel.elastic4s.{ElasticsearchClientUri, BulkCompatibleDefinition, ElasticClient, ElasticDsl}
import org.reactivestreams.{Publisher, Subscriber}
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import no.finn.repoindexer.FileType._
import com.sksamuel.elastic4s.streams.ReactiveElastic._

import no.finn.repoindexer.{FileType, IndexCandidate, IndexFile, IndexRepo}
import org.apache.logging.log4j.LogManager
import org.elasticsearch.common.settings.ImmutableSettings
import akka.stream.scaladsl._
import scala.annotation.tailrec
import scala.util.Try
import scala.collection.JavaConverters._
object Indexing {
  val config = ConfigFactory.load()
  val log = LogManager.getLogger()
  val localRepoFolder = config.getString("repo.folder")
  val client = {
    val cluster = config.as[Option[String]]("es.cluster").getOrElse("elasticsearch")
    val url = config.as[Option[String]]("es.url").getOrElse("localhost")
    val port = config.as[Option[Int]]("es.port").getOrElse(9300)
    val settings = ImmutableSettings.builder().put("cluster.name", cluster).build()
    log.info(s"Connecting to ${url}:${port}, cluster: ${cluster}")
    val uri = ElasticsearchClientUri.apply(s"${url}:${port}")
    ElasticClient.remote(settings, uri)
  }
  implicit val indexDocumentBuilder = new RequestBuilder[IndexCandidate] {
    import ElasticDsl._
    def request(doc: IndexCandidate):BulkCompatibleDefinition = index into "sources" / "file" fields (
      "slug" -> doc.slug,
      "project" -> doc.project,
      "filename" -> doc.file.getName,
      "imports" -> doc.imports.map { i =>
        i.getName.getName
      },
      "fullyQualifiedImport" -> doc.imports.map { i =>
        i.toString
      },
      "package" -> doc.packageName.map(p => p.getName.getName),
      "packageName" -> doc.packageName.map(p => p.getName),
      "fullyQualifiedPackage" -> doc.packageName.map(p => p.toString),
      "types" -> doc.typeDeclarations.map { tDecl =>
        tDecl.toString
      },
      "content" -> doc.content
    )
  }

  val fileSource : Source[IndexRepo, Unit] = {
    val localRepo = new File(localRepoFolder)
    if (localRepo.exists()) {
      val repos = new File(localRepoFolder).list.map { repo =>
        IndexRepo(new File(localRepoFolder, repo), findSlug(repo), repo)
      }
      Source(repos.toList)
    } else {
      Source.empty
    }
  }
  val indexFilesFlow : Flow[IndexRepo, IndexFile, Unit] = {
    Flow[IndexRepo].map { repo =>
      listFiles(repo.path).map { f =>
        IndexFile(f, repo.slug, repo.path.getName)
      } filter { indexFile =>
        shouldIndex(indexFile.file)
      }
    } mapConcat{ identity }

  }

  val readFilesFlow : Flow[IndexFile, IndexCandidate, Unit] = {
    Flow[IndexFile].map { indexFile =>
      val content = io.Source.fromFile(indexFile.file).getLines().toList.mkString("")
      IndexCandidate(findFileType(indexFile.file), indexFile.slug, indexFile.project, indexFile.file, content)
    }
  }

  val enrichJavaFiles : Flow[IndexCandidate, IndexCandidate, Unit] = {
    Flow[IndexCandidate].map { candidate =>
      candidate.fileType match {
        case JAVA => enrichFromCompilationUnit(candidate.file, candidate)
        case OTHER => candidate
      }
    }
  }


  val fullIndexFlow = Stash.cloneCandidatesFlow
    .via(Cloner.cloneFlow)
    .via(indexFilesFlow)
    .via(readFilesFlow)
    .via(enrichJavaFiles)


  private def findFileType(file: File): FileType = {
    if (file.isFile() && file.getName().endsWith(".java")) {
      JAVA
    } else {
      OTHER
    }
  }

  def findSlug(repo: String) = {
    repo.replaceAll("ssh---git-git-finn-no-7999-", "").split("-").drop(1).filter(_ != "git").mkString("-")
  }

  def findProject(repo:String) = {
    repo.replaceAll("ssh---git-git-finn-no-7999-", "").split("-").head
  }
  private def enrichFromCompilationUnit(file: File, candidate: IndexCandidate): IndexCandidate = {
    Try {
        JavaParser.parse(file)
    } map { compilationUnit =>
      val imports: List[ImportDeclaration] = compilationUnit.getImports.asScala.toList
      val types = compilationUnit.getTypes.asScala.toList
      val packageName = compilationUnit.getPackage
      candidate.copy(imports = imports, packageName = Some(packageName), content = compilationUnit.toStringWithoutComments)
    } getOrElse {
      candidate
    }
  }
  def runReindex() : Unit = {
      import com.sksamuel.elastic4s.streams.ReactiveElastic._
      implicit lazy val actorSystem = ActorSystem("RepoIndexer")
      implicit val actorMat = ActorMaterializer()
      val docPublisher: Publisher[IndexCandidate] = fullIndexFlow.runWith(Sink.publisher)
      val esSubscriber = client.subscriber[IndexCandidate]()
      docPublisher.subscribe(esSubscriber)
  }

  private def listFiles(file: File): List[File] = {
    @tailrec
    def listFiles(files: List[File], result: List[File]): List[File] = files match {
      case Nil => result
      case head :: tail if head.isDirectory =>
        listFiles(Option(head.listFiles.filter(f => f.getName != ".git")).map(_.toList ::: tail).getOrElse(tail), result)
      case head :: tail if head.isFile =>
        listFiles(tail, head :: result)
    }
    listFiles(List(file), Nil)
  }

  private val includeExtensions = Seq(".java", ".scala", ".xml", ".md", ".groovy", ".gradle", ".sbt")
  private def shouldIndex(file: File) = includeExtensions.exists(extension => file.getPath.endsWith(extension))


}
