package no.finn.repoindexer.actors

import akka.actor.{Actor, ActorLogging}
import no.finn.repoindexer.IndexRepo

class ProjectReaderActor extends Actor with ActorLogging {
  def receive = {
    case IndexRepo(path) =>
      log.info("Indexing repo at "+path)
  }
}
