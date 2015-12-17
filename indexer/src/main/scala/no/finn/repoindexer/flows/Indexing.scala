package no.finn.repoindexer.flows

import java.io.File
import java.time.Instant

import akka.stream.scaladsl._
import ammonite.ops._
import com.github.javaparser.JavaParser
import com.github.javaparser.ast.{CompilationUnit, ImportDeclaration}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.analyzers.{CustomAnalyzerDefinition, StandardTokenizer, StopTokenFilter}
import com.sksamuel.elastic4s.mappings.FieldType.StringType
import com.sksamuel.elastic4s.streams.RequestBuilder
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import no.finn.repoindexer.IdxProcess._
import no.finn.repoindexer._
import org.apache.logging.log4j.LogManager
import org.elasticsearch.common.settings.Settings

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object Indexing {
  val config = ConfigFactory.load()
  val log = LogManager.getLogger()
  val localRepoFolder = config.getString("repo.folder")
  val localFolder = Path(localRepoFolder)
  val client = {
    val cluster = config.as[Option[String]]("es.cluster").getOrElse("elasticsearch")
    val url = config.as[Option[String]]("es.url").getOrElse("localhost")
    val port = config.as[Option[Int]]("es.port").getOrElse(9300)
    val settings = Settings.builder().put("cluster.name", cluster).build()
    log.info(s"Connecting to ${url}:${port}, cluster: ${cluster}")
    val uri = ElasticsearchClientUri.apply(s"${url}:${port}")
    ElasticClient.remote(settings, uri)
  }
  implicit val indexDocumentBuilder = new RequestBuilder[IndexCandidate] {


    def request(doc: IndexCandidate): BulkCompatibleDefinition = index into "sources" / "file" fields Map(
      "slug" -> doc.slug,
      "project" -> doc.project,
      "filename" -> doc.path.last,
      "extension" -> doc.path.ext,
      "content" -> doc.content,
      "added" -> Instant.now(),
      "relativePath" -> doc.path.relativeTo(localFolder)
    ) ++ doc.javaClass.map(processJavaClass).getOrElse(Map())

  }

  def stripSemiColon(s: String): String = s.replaceAll(";", "")

  def pathToUrl(repo: Path): String = ""

  val fileSource: Source[IndexRepo, Unit] = {
    val localRepo = Path(localRepoFolder)
    mkdir ! localRepo
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
        read ! idxFile.path
      }
      val dataString = content match {
        case Success(d) => d
        case Failure(_) => ""
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

  def processJavaClass(jc: JavaClassInfo):Map[String, Any] = {
    Map("imports" -> jc.imports,
    "fullyQualifiedImport" -> jc.imports.map { i =>
      stripSemiColon(i.toString)
    },
    "package" -> jc.packageName.map(p => p.getName.getName).getOrElse(""),
    "packageName" -> jc.packageName.map(p => p.getName).getOrElse(""),
    "types" -> jc.typeDeclarations.map { tDecl => stripSemiColon(tDecl.toString) }
    )
  }

  val indexFlow: Flow[IndexCandidate, IndexResult, Unit] = {
    Flow[IndexCandidate].mapAsyncUnordered(4) { doc =>
      val content = Map(
        "slug" -> doc.slug,
        "project" -> doc.project,
        "filename" -> doc.path.last,
        "extension" -> doc.path.ext,
        "content" -> doc.content,
        "added" -> Instant.now,
        "relativePath" -> doc.path.relativeTo(localFolder)
      )
      val jcData = doc.javaClass.map(processJavaClass).getOrElse(Map())

      client.execute {
        index into "sources" / "file" fields content ++ jcData
      }
    }
  }

  val fullIndexFlow = Stash.cloneCandidatesFlow
    .via(Cloner.cloneFlow)
    .via(indexFilesFlow)
    .via(readFilesFlow)

  val indexer = fullIndexFlow.via(indexFlow)

  val indexRepoFlow: Flow[StashRepo, IndexResult, Unit] = {
    Flow[StashRepo].mapAsyncUnordered(4) { repo =>
      client.execute {
        index into "repositories" / "repo" fields(
          "slug" -> repo.slug,
          "url" -> repo.cloneUrl,
          "name" -> repo.name
          )
      }
    }
  }

  //  val repoIndexing = Stash.cloneCandidatesFlow.via(indexRepoFlow)
  val stashRepoIndexing = Stash.repositoriesFlow.via(indexRepoFlow)

  private def findFileType(file: File): IdxProcess = {
    if (file.isFile() && file.getName().endsWith(".java")) {
      JAVA
    } else {
      OTHER
    }
  }

  def findSlug(repo: String) = {
    println(repo)
    repo.replaceAll("ssh---git-git-finn-no-7999-", "").split("-").drop(1).filter(_ != "git").mkString("-")
  }

  def findProject(repo: String) = {
    repo.replaceAll("ssh---git-git-finn-no-7999-", "").split("-").head
  }

  def getImports(compilationUnit: CompilationUnit): List[ImportDeclaration] = {
    Option {
      compilationUnit.getImports
    } map { _.asScala.toList } getOrElse List()
  }

  def getPackageName(compilationUnit: CompilationUnit) = {
    Option {
      compilationUnit.getPackage
    }
  }

  private def enrichFromCompilationUnit(file: File, candidate: IndexCandidate): IndexCandidate = {
    Try {
      println(s"Parsing ${file}")
      JavaParser.parse(file)
    } map { compilationUnit =>
      val impo = getImports(compilationUnit)
      val pName = getPackageName(compilationUnit)
      val classInfo = compilationUnit.getClass
      val typeDecl = compilationUnit.getTypes
      val implements = classInfo.getInterfaces
      val extendsClass = classInfo.getSuperclass
      val methods = classInfo.getMethods.map(m => m.getName)
      val className = classInfo.getCanonicalName
      val javaCl= JavaClassInfo(className, impo, pName, typeDecl.asScala.toList, implements, extendsClass, methods)
      candidate.copy(javaClass = Some(javaCl))
    } getOrElse {
      candidate
    }
  }

  val javaStopWords = List("public", "private", "protected", "interface",
    "abstract", "implements", "extends", "null", "new",
    "switch", "case", "default", "synchronized",
    "do", "if", "else", "break", "continue", "this",
    "assert", "for", "instanceof", "transient",
    "final", "static", "void", "catch", "try",
    "throws", "throw", "class", "finally", "return",
    "const", "native", "super", "while", "import",
    "package", "true", "false")

  def createIndex(): Unit = {
    client.execute {
      create index "sources" shards 1 mappings {
        "file" as {
          "slug" typed StringType
          "project" typed StringType
          "content" typed StringType analyzer "code"
        }
      } analysis {
        CustomAnalyzerDefinition("code",
          StandardTokenizer("myanalyzer"),
          StopTokenFilter("stop token", javaStopWords)
        )
      }
    }
  }

  val fileReadingFlow = fileSource
    .via(indexFilesFlow)
    .via(readFilesFlow)
    .via(enrichJavaFiles)

  val indexWithJava = fileReadingFlow.via(indexFlow)

  private val includeExtensions = Seq("java", "scala", "xml", "md", "groovy", "gradle", "sbt", "ini", "properties")

  private def shouldIndex(file: File) = includeExtensions.exists(extension => file.getPath.endsWith(extension))

  private def shouldIndexAmmo(path: Path) = path.isFile && includeExtensions.contains(path.ext)


}
