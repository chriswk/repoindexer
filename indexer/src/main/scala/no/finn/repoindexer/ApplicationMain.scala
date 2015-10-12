package no.finn.repoindexer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import com.typesafe.config.ConfigFactory
import dispatch._
import org.json4s.DefaultFormats
import scala.util.{Success, Failure}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonDSL._
import scala.concurrent.ExecutionContext.Implicits.global



object ApplicationMain {
  val config = ConfigFactory.load()
  val userName = config.getString("stash.username")
  val password = config.getString("stash.password")
  val baseUrl = url(config.getString("stash.url"))
  val apiPath = baseUrl / "rest" / "api" / "1.0"
  val projectsUrl = apiPath / "projects"

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

  val cloneUrls = {
    repoSource.map { repo =>
      val url = repo.links("clone").find(l => l.name match {
        case Some(name) => name == "ssh"
        case None => false
      }) match {
        case Some(link) => (link, repo.slug)
        case None =>
      }
    }
  }
}
