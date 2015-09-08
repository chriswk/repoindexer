package no.finn.repoindexer.actors

import akka.actor.{Actor, ActorLogging, Props}
import dispatch.Defaults._
import dispatch._
import no.finn.repoindexer.{CloneRepo, GetRepositories, RepoResponse}
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.util.{Failure, Success}

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
            val url = repo.links("clone").find(l => l.name match {
              case Some(name) => name == "ssh"
              case None => false
            })
            url match {
              case Some(url) => cloner ! CloneRepo(url, repo.slug)
              case None =>
            }
          }
        }
        case Failure(t) => log.info("Something went wrong")
      }
    }
  }
}

