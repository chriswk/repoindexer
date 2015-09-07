package no.finn.repoindexer.actors

import akka.actor.{Actor, ActorLogging}
import no.finn.repoindexer.{Project, GetProjects}
import dispatch._, Defaults._
import scala.util.{Success, Failure}
class StashProjectsActor extends Actor with ActorLogging with StashActor {
  val projectsUrl = apiPath / "projects?limit=1000"
  val url = projectsUrl.as_!(userName, password)
  def receive = {
    case GetProjects => {
      val response = Http(url OK as.String)
      response onComplete {
        case Success(content) => {
          log.info(content)
          sender() ! Seq(Project("Applications", "APPS"), Project("Modules", "MODS"))
        }
        case Failure(t) => {
          log.info("Handle error")
        }
      }
    }
  }
}
