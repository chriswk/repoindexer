package no.finn.repoindexer.actors

import akka.actor.{Actor, ActorLogging}
import no.finn.repoindexer.GetProjects
import dispatch._, Defaults._
import scala.util.{Success, Failure}
class StashProjectsActor extends Actor with ActorLogging with StashActor {
  val projectsUrl = apiPath / "projects"
  val url = projectsUrl.as_!(userName, password)
  def receive = {
    case GetProjects(_) => {
      val response = Http(url OK as.String)
      response onComplete {
        case Success(content) => {
          log.info(content)
        }
        case Failure(t) => {
          log.info("Handle error")
        }
      }
    }
  }

}
