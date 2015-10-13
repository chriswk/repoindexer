package no.finn.repoindexer

import java.io.{FileInputStream, File}
import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.sksamuel.elastic4s.ElasticDsl.index
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import com.jcraft.jsch.{Session, JSch}
import com.sksamuel.elastic4s.{ElasticsearchClientUri, ElasticClient}
import dispatch._
import org.apache.logging.log4j.{LogManager, Logger}
import org.eclipse.jgit.api.{TransportConfigCallback, Git}
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.TextProgressMonitor
import org.eclipse.jgit.transport.{SshTransport, Transport, OpenSshConfig, JschConfigSessionFactory}
import org.eclipse.jgit.util.FS
import org.elasticsearch.common.settings.ImmutableSettings
import org.json4s.DefaultFormats
import resource._
import scala.annotation.tailrec
import scala.util.{Try, Success, Failure}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonDSL._
import scala.concurrent.ExecutionContext.Implicits.global



object ApplicationMain {
  val config = ConfigFactory.load()
  val userName = config.getString("stash.username")
  val password = config.getString("stash.password")
  val baseUrl = url(config.getString("stash.url"))
  val localRepoFolder = config.getString("repo.folder")
  val privateKeyPath = config.getString("bamboo.privatekey")
  val privateKeyPass = config.getString("bamboo.privatepass")
  val apiPath = baseUrl / "rest" / "api" / "1.0"
  val projectsUrl = apiPath / "projects"
  val log = LogManager.getLogger()
  val client = {
    val cluster = config.as[Option[String]]("es.cluster").getOrElse("elasticsearch")
    val url = config.as[Option[String]]("es.url").getOrElse("localhost")
    val port = config.as[Option[Int]]("es.port").getOrElse(9300)
    val settings = ImmutableSettings.builder().put("cluster.name", cluster).build()
    log.info(s"Connecting to ${url}:${port}, cluster: ${cluster}")
    val uri = ElasticsearchClientUri.apply(s"${url}:${port}")
    ElasticClient.remote(settings, uri)
  }

  class CustomConfigSessionFactory extends JschConfigSessionFactory {
    override protected def getJSch(hc: OpenSshConfig.Host, fs: FS) : JSch = {
      val jsch = super.getJSch(hc, fs)
      jsch.removeAllIdentity()
      jsch.addIdentity(privateKeyPath, privateKeyPass)
      jsch
    }
    override protected def configure(hc: OpenSshConfig.Host, session: Session): Unit = {

    }
  }
  val progressMonitor = new TextProgressMonitor()

  val transportConfig = new TransportConfigCallback() {
    override def configure(transport: Transport): Unit = {
      val sshTransport = transport.asInstanceOf[SshTransport]
      sshTransport.setSshSessionFactory(sshSessionFactory)
    }
  }
  val sshSessionFactory = new CustomConfigSessionFactory()

  implicit val formats = DefaultFormats

  def authenticatedRequest(url: Req) = {
    Http(url.as_!(userName, password) OK as.String)
  }
  val stringFlow = Flow[String].map(s => s + "_postfix")

  def run(s: Source[List[Project], Unit]): Unit = {
    implicit lazy val system = ActorSystem("RepoIndexer")
    implicit val materializer = ActorMaterializer()
    s.runWith(Sink.foreach(println(_))).onComplete {
      _ => system.terminate()
    }
  }

  /** Performs the initial query against the base url **/
  val projectListSource: Source[List[Project], Unit] = {
    val r = authenticatedRequest(projectsUrl)
      .map(parse(_))
      .map(data => data.extract[ProjectResponse].values)
    Source(r)
  }


  val projectUrlSource: Source[Project, Unit] = {
    projectListSource.mapConcat(identity)
  }

  val repoReqSource : Source[Req, Unit] = {
    projectUrlSource.map { project =>
      apiPath / "projects" / project.key / "repos"
    }
  }


  val repoListSource : Source[List[StashRepo], Unit] = {
    repoReqSource.mapAsync(2)(r => {
      authenticatedRequest(r)
        .map(parse(_))
        .map(data => data.extract[RepoResponse].values)
    })
  }

  val repoSource : Source[StashRepo, Unit]= {
    repoListSource.mapConcat(p => p.map(f => f))
  }

  val cloneUrls : Source [CloneRepo, Unit] = {
    repoSource.map { repo =>
      val url = repo.links("clone").find(l => l.name match {
        case Some(name) => name == "ssh"
        case None => false
      })
      url match {
        case Some(link) => CloneRepo(Some(link), repo.slug)
        case None => CloneRepo(None, repo.slug)
      }
    } filter { r => r.sshClone.isDefined }
  }

  val cloner: Source[IndexRepo, Unit] = {
    cloneUrls.mapAsync(4)(c => {
      println(c)
      c.sshClone match {
        case Some(cloneUrl) => {
          val localPath = new File(localRepoFolder, c.slug)
          Future {
            if (localPath.exists) {
              val pullResult = new Git(new FileRepository(new File(localPath, ".git")))
                .pull()
                .setTransportConfigCallback(transportConfig)
                .setProgressMonitor(progressMonitor)
                .call()

            } else {
              localPath.mkdirs()
              val url = cloneUrl.href
              for {
                repo <- managed(Git
                  .cloneRepository()
                  .setURI(url)
                  .setDirectory(localPath)
                  .setTransportConfigCallback(transportConfig)
                  .setProgressMonitor(progressMonitor)
                  .call()
                )
              } {

              }
            }
            IndexRepo(localPath, c.slug)
          }
        }
        case None => Future.failed(new IllegalStateException("No cloneurl"))
      }
    })
  }

  val filesToIndex : Source[IndexFile, Unit] = {
    cloner.mapConcat(idxRepo => {
      listFiles(idxRepo.path).map { f =>
        IndexFile(f, idxRepo.slug)
      }
    }) filter { f =>
      shouldIndex(f.file)
    }
  }

  val fileIdentifier : Source[IndexCandidate, Unit] = {
    filesToIndex.map { f => {
      if (f.file.getName.endsWith(".java")) {
        IndexCandidate(f, FileType.JAVA)
      } else {
        IndexCandidate(f, FileType.OTHER)
      }
    }}
  }


  def getCompilationUnit(is: java.io.InputStream): Try[CompilationUnit] = Try {
    JavaParser.parse(is)
  }
  private def listFiles(file: File): List[File] = {
    @tailrec
    def listFiles(files: List[File], result: List[File]): List[File] = files match {
      case Nil => result
      case head :: tail if head.isDirectory =>
        listFiles(Option(head.listFiles).map(_.toList ::: tail).getOrElse(tail), result)
      case head :: tail if head.isFile =>
        listFiles(tail, head :: result)
    }
    listFiles(List(file), Nil)
  }

  private val includeExtensions = Seq(".java", ".scala", ".xml", ".md", ".groovy", ".gradle", ".sbt")
  private def shouldIndex(file: File) = includeExtensions.exists(extension => file.getPath.endsWith(extension))

}
