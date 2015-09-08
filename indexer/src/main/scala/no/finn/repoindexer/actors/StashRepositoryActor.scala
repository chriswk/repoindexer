package no.finn.repoindexer.actors

import akka.actor.{Props, ActorLogging, Actor}
import dispatch._, Defaults._
import scala.util.{Success, Failure}

import no.finn.repoindexer.{RepoResponse, CloneRepo, GetRepositories}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonDSL._

class StashRepositoryActor extends Actor with ActorLogging with StashActor {
  def cloner = context.actorOf(Props[StashCloneActor])


  def receive = {
    case GetRepositories(key, link) => {
      val repositoriesUrl = apiPath / "projects" / key / "repos"
      log.info(s"Getting repositories from ${repositoriesUrl.url}")
      val repos = authenticatedRequest(repositoriesUrl).map(c => parse(c))
      repos onComplete {
        case Success(content) => {
          content.extract[RepoResponse].values.foreach { repo =>
            cloner ! CloneRepo(repo.cloneUrl.replaceAll(userName, "").replaceAll("@", ""), repo.slug)
          }
        }
        case Failure(t) => log.info("Something went wrong")
      }
    }
  }
}
