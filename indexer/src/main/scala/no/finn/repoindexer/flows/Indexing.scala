package no.finn.repoindexer.flows

import java.io.File

import akka.stream.scaladsl._
import ammonite.ops._
import com.github.javaparser.JavaParser
import com.github.javaparser.ast.ImportDeclaration
import com.sksamuel.elastic4s.streams.RequestBuilder
import com.sksamuel.elastic4s.{BulkCompatibleDefinition, ElasticClient, ElasticDsl, ElasticsearchClientUri}
import ElasticDsl._
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import no.finn.repoindexer.IdxProcess._
import no.finn.repoindexer._
import org.apache.logging.log4j.LogManager
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.common.settings.ImmutableSettings

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

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


    def request(doc: IndexCandidate): BulkCompatibleDefinition = index into "sources" / "file" fields(
      "slug" -> doc.slug,
      "project" -> doc.project,
      "filename" -> doc.path.last,
      "extension" -> doc.path.ext,
      "imports" -> doc.imports.map { i =>
        i.getName.getName
      },
      "fullyQualifiedImport" -> doc.imports.map { i =>
        stripSemiColon(i.toString)
      },
      "package" -> doc.packageName.map(p => p.getName.getName).getOrElse(""),
      "packageName" -> doc.packageName.map(p => p.getName).getOrElse(""),
      "fullyQualifiedPackage" -> doc.packageName.map(p => stripSemiColon(p.toString)).getOrElse(""),
      "types" -> doc.typeDeclarations.map { tDecl =>
        stripSemiColon(tDecl.toString)
      },
      "content" -> doc.content
      )
  }

  def stripSemiColon(s: String): String = s.replaceAll(";", "")

  def pathToUrl(repo: Path): String = ""

  val fileSource: Source[IndexRepo, Unit] = {
    val localRepo = Path(localRepoFolder)
    val repoList = ls ! localRepo | (repo => IndexRepo(repo, repo.last, pathToUrl(repo)))
    Source(repoList.toList)
  }

  val indexFilesFlow: Flow[IndexRepo, IndexFile, Unit] = {
    Flow[IndexRepo].map { repo =>
      (ls.rec ! repo.path |? (file => shouldIndexAmmo(file))).toList.map { p =>
        IndexFile(p, findSlug(repo.slug), repo.path.last)
      }
    } mapConcat {
      identity
    }
  }

  def findFileTypeAmmo(path: Path): IdxProcess = {
    if (path.isFile && path.ext == "java")
      JAVA
    else
      OTHER
  }

  val readFilesFlow: Flow[IndexFile, IndexCandidate, Unit] = {
    Flow[IndexFile].map { idxFile =>
      val content = Try {
        read! idxFile.path
      }
      val dataString = content match {
        case scala.util.Success(d) => d
        case scala.util.Failure(_) => ""
      }
      IndexCandidate(findFileTypeAmmo(idxFile.path), idxFile.slug, idxFile.project, idxFile.path, dataString)
    }
  }

  val enrichJavaFiles: Flow[IndexCandidate, IndexCandidate, Unit] = {
    Flow[IndexCandidate].map { candidate =>
      candidate.fileType match {
        case JAVA => enrichFromCompilationUnit(new File(candidate.path.toString), candidate)
        case OTHER => candidate
      }

    }
  }
  val indexFlow: Flow[IndexCandidate, IndexResponse, Unit] = {
    Flow[IndexCandidate].mapAsyncUnordered(4) { doc =>
      client.execute {
        index into "sources" / "file" fields(
          "slug" -> doc.slug,
          "project" -> doc.project,
          "filename" -> doc.path.last,
          "extension" -> doc.path.ext,
          "imports" -> doc.imports.map { i =>
            i.getName.getName
          },
          "fullyQualifiedImport" -> doc.imports.map { i =>
            stripSemiColon(i.toString)
          },
          "package" -> doc.packageName.map(p => p.getName.getName).getOrElse(""),
          "packageName" -> doc.packageName.map(p => p.getName).getOrElse(""),
          "fullyQualifiedPackage" -> doc.packageName.map(p => stripSemiColon(p.toString)).getOrElse(""),
          "types" -> doc.typeDeclarations.map { tDecl =>
            stripSemiColon(tDecl.toString)
          },
          "content" -> doc.content
          )
      }
    }
  }

  val fullIndexFlow = Stash.cloneCandidatesFlow
    .via(Cloner.cloneFlow)
    .via(indexFilesFlow)
    .via(readFilesFlow)
    .via(enrichJavaFiles)


  private def findFileType(file: File): IdxProcess = {
    if (file.isFile() && file.getName().endsWith(".java")) {
      JAVA
    } else {
      OTHER
    }
  }

  def findSlug(repo: String) = {
    repo.replaceAll("ssh---git-git-finn-no-7999-", "").split("-").drop(1).filter(_ != "git").mkString("-")
  }

  def findProject(repo: String) = {
    repo.replaceAll("ssh---git-git-finn-no-7999-", "").split("-").head
  }

  private def enrichFromCompilationUnit(file: File, candidate: IndexCandidate): IndexCandidate = {
    Try {
      JavaParser.parse(file)
    } map { compilationUnit =>
      val imports: List[ImportDeclaration] = compilationUnit.getImports.asScala.toList
      val types = compilationUnit.getTypes.asScala.toList
      val packageName = compilationUnit.getPackage
      candidate.copy(imports = imports, packageName = Some(packageName))
    } getOrElse {
      candidate
    }
  }


  val fileReadingFlow = fileSource
    .via(indexFilesFlow)
    .via(readFilesFlow)
    .via(enrichJavaFiles)


  private val includeExtensions = Seq("java", "scala", "xml", "md", "groovy", "gradle", "sbt")

  private def shouldIndex(file: File) = includeExtensions.exists(extension => file.getPath.endsWith(extension))

  private def shouldIndexAmmo(path: Path) = path.isFile && includeExtensions.contains(path.ext)


}
