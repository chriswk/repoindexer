package no.finn.repoindexer.actors

import akka.actor.{Actor, ActorLogging}
import no.finn.repoindexer.IndexFile

class ElasticSearchActor extends Actor with ActorLogging {
  def receive = {
    case IndexFile(file) => log.info(s"Index file $file")
  }
}
