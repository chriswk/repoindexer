package no.finn.repoindexer.flows

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, Authorization}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import no.finn.repoindexer._
import org.apache.logging.log4j.LogManager
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods._
import akka.stream.scaladsl._
import akka.http.scaladsl.Http
import HttpMethods._
import scala.concurrent.ExecutionContext.Implicits.global

object Stash {
  val config = ConfigFactory.load()
  val stashUsername = config.getString("stash.username")
  val stashPassword = config.getString("stash.password")
  val stashBaseUrl = config.getString("stash.url")
  val apiPath = s"${stashBaseUrl}/rest/api/1.0"
  val projectsUrl = s"${apiPath}/projects"
  val log = LogManager.getLogger()

  implicit val formats = DefaultFormats
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()


  def stashAuthenticatedRequest(url: String) = {
    val authorization = BasicHttpCredentials(stashUsername, stashPassword)
    val req = HttpRequest(GET, uri = url, headers = List(headers.Authorization(authorization)))
    Http().singleRequest(req)
          .map(res => res.entity.dataBytes.runFold(ByteString(""))(_ ++ _))
          .flatMap(bs => bs.map(_.decodeString("ISO8859-1")))
  }

  /** Performs the initial query against the base url **/
  val projectListSource: Source[List[Project], Unit] = {
    val r = stashAuthenticatedRequest(projectsUrl)
      .map(parse(_))
      .map(data => data.extract[ProjectResponse].values)
    Source(r)
  }

  val projectUrlFlow:Flow[List[Project], Project, Unit] = Flow[List[Project]].mapConcat { identity }


  val repoReqFlow : Flow[Project, String, Unit] = Flow[Project].map { project =>
    s"${apiPath}/projects/${project.key}/repos?limit=500"
  }



  val repoListFlow : Flow[String, List[StashRepo], Unit] = Flow[String].mapAsyncUnordered(2) { r =>
    stashAuthenticatedRequest(r)
      .map(parse(_))
      .map(data => data.extract[RepoResponse].values)
  }


  val repoFlow : Flow[List[StashRepo], StashRepo, Unit] = Flow[List[StashRepo]].mapConcat { identity }

  val cloneFlow : Flow[StashRepo, CloneRepo, Unit] = Flow[StashRepo].map { repo =>
    val url = repo.links("clone").find(l => l.name match {
      case Some(name) => name == "ssh"
      case None => false
    })
    url match {
      case Some(link) => CloneRepo(Some(link), repo.slug)
      case None => CloneRepo(None, repo.slug)
    }
  }

  val cloneCandidatesFlow = projectListSource
    .via(projectUrlFlow)
    .via(repoReqFlow)
    .via(repoListFlow)
    .via(repoFlow)
    .via(cloneFlow)

}
