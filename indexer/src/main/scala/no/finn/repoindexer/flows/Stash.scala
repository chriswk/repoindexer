package no.finn.repoindexer.flows

import akka.stream.scaladsl.{Flow, Source}
import dispatch._
import Defaults._
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import no.finn.repoindexer._
import org.apache.logging.log4j.LogManager
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods._
import akka.stream.scaladsl._


object Stash {
  val config = ConfigFactory.load()
  val stashUsername = config.getString("stash.username")
  val stashPassword = config.getString("stash.password")
  val stashBaseUrl = url(config.getString("stash.url"))
  val apiPath = stashBaseUrl / "rest" / "api" / "1.0"
  val projectsUrl = apiPath / "projects"
  val log = LogManager.getLogger()

  implicit val formats = DefaultFormats


  def stashAuthenticatedRequest(url: Req) = {
    val req = url.as_!(stashUsername, stashPassword)
      .setContentType("application/json", "utf-8")
      .addQueryParameter("limit", "1000")
    println(s"Requesting ${req.url}")
    Http(req OK as.String)
  }

  /** Performs the initial query against the base url **/
  val projectListSource: Source[List[Project], Unit] = {
    val r = stashAuthenticatedRequest(projectsUrl)
      .map(parse(_))
      .map(data => data.extract[ProjectResponse].values)
    Source(r)
  }

  val projectUrlFlow:Flow[List[Project], Project, Unit] = Flow[List[Project]].mapConcat { identity }


  val repoReqFlow : Flow[Project, Req, Unit] = Flow[Project].map { project =>
    apiPath / "projects" / project.key / "repos"
  }



  val repoListFlow : Flow[Req, List[StashRepo], Unit] = Flow[Req].mapAsync(2) { r =>
    println(s"${r.url}")
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
