package no.finn.repoindexer.actors

import akka.actor.{Props, Actor, ActorLogging}
import no.finn.repoindexer._
import dispatch._, Defaults._
import scala.util.{Success, Failure}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonDSL._


class StashProjectsActor extends Actor with ActorLogging with StashActor {
  val repoActor = context.actorOf(Props[StashRepositoryActor])
  val projectsUrl = apiPath / "projects"
  def receive = {
    case GetProjects => {
      val response = authenticatedRequest(projectsUrl).map(c => parse(c))
      response onComplete {
        case Success(content) => {
          content.extract[ProjectResponse].values.foreach { project =>
            repoActor ! GetRepositories(project.key, project.link.url)
          }
        }
        case Failure(t) => {
          log.error(s"Failed while getting ${projectsUrl.url}", t)
        }
      }
    }
  }
}
