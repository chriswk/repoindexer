package no.finn.repoindexer.actors

import akka.actor.{Actor, ActorLogging}
import no.finn.repoindexer.GetProjects

class StashProjectsActor extends Actor with ActorLogging {

  def receive = {
    case GetProjects(stashUrl) => {
      log.info(s"Scanning ${stashUrl}")
      context.system.shutdown()
    }
  }

}
