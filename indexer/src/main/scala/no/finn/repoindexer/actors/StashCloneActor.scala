package no.finn.repoindexer.actors

import akka.actor.{Actor, ActorLogging}
import no.finn.repoindexer.CloneRepo

class StashCloneActor extends Actor with ActorLogging with StashActor {

  def receive = {
    case CloneRepo(cloneUrl) => {
      log.info(s"Will clone ${cloneUrl}")
    }
  }
}
