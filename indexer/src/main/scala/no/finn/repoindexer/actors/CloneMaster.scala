package no.finn.repoindexer.actors

import akka.actor.{Props, Actor, ActorLogging}
import akka.routing.{Router, RoundRobinRoutingLogic, ActorRefRoutee}
import no.finn.repoindexer.CloneRepo

class CloneMaster extends Actor with ActorLogging {

  val router = {
    var i = 0
    val routees = Vector.fill(8) {
      val r = context.actorOf(Props[StashCloneActor], s"cloner-${i}")
      i = i+1
      context watch r
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  def receive = {
    case cr@CloneRepo(_, _) => router.route(cr, sender())
  }
}
