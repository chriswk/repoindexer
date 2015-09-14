package no.finn.repoindexer.actors

import akka.actor.{Actor, ActorLogging}
import no.finn.repoindexer.IndexFile
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import org.elasticsearch.common.settings.ImmutableSettings
import scala.concurrent.ExecutionContext.Implicits.global

class ElasticSearchActor extends Actor with ActorLogging {
  val config = ConfigFactory.load()
  val client = {
    val cluster = config.as[Option[String]]("es.cluster").getOrElse("elasticsearch")
    val url = config.as[Option[String]]("es.url").getOrElse("localhost")
    val port = config.as[Option[Int]]("es.port").getOrElse(9300)
    val settings = ImmutableSettings.builder().put("cluster.name", cluster).build()
    log.info(s"Connecting to ${url}:${port}, cluster: ${cluster}")
    ElasticClient.remote(settings, (url, port))
  }

  def receive = {
    case IndexFile(file, slug) => {
      val source = io.Source.fromFile(file)
      val lines = try source.getLines mkString "\n" finally source.close()
      client.execute {
        index into "sources" fields (
          "repo" -> slug,
          "name" -> file.getName,
          "content" -> lines
        )
      } onSuccess {
        case res => log.info(s"Done indexing ${file.getName} in repo: ${slug}")
      }
    }
  }
}
