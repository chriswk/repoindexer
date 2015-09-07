package no.finn.repoindexer.actors

import akka.actor.{Actor, ActorLogging}
import no.finn.repoindexer.{Project, GetProjects}
import akka.actor.{Props, Actor, ActorLogging}
import no.finn.repoindexer.{GetRepositories, GetProjects}
import dispatch._, Defaults._
import scala.util.{Success, Failure}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonDSL._

case class Link(url: String, rel: String)
case class ProjectResponse(values: List[Project])

case class Project(key: String, id: Long, name: String, public: Boolean, link: Link)
class StashProjectsActor extends Actor with ActorLogging with StashActor {
  val repoActor = context.actorOf(Props[StashRepositoryActor])
  val projectsUrl = apiPath / "projects"
  def receive = {
    case GetProjects() => {
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
