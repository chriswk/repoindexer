package no.finn.repoindexer.flows

import java.io.File

import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri}
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._

import no.finn.repoindexer.{FileType, IndexCandidate, IndexFile, IndexRepo}
import org.apache.logging.log4j.LogManager
import org.elasticsearch.common.settings.ImmutableSettings
import akka.stream.scaladsl._

import scala.annotation.tailrec
import scala.util.Try

object Indexing {
  val config = ConfigFactory.load()
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

  val indexFilesFlow : Flow[IndexRepo, IndexFile, Unit] = {
    Flow[IndexRepo].map { repo =>
      listFiles(repo.path).map { f =>
        IndexFile(f, repo.slug)
      }
    } mapConcat{ identity }
  }

  val identifyFilesFlow : Flow[IndexFile, IndexCandidate, Unit] = {
    Flow[IndexFile].map { file =>
      if (file.file.getName.endsWith(".java")) {
        IndexCandidate(file, FileType.JAVA)
      } else {
        IndexCandidate(file, FileType.OTHER)
      }
    }
  }

  val enrichJavaFiles : Flow[IndexCandidate, IndexCandidate, Unit] = {
    Flow[IndexCandidate].map { candidate =>
      candidate.fileType match {
        case FileType.JAVA => candidate
        case FileType.OTHER => candidate
      }
    }
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
