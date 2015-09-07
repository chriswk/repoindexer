package no.finn.repoindexer

import akka.actor.ActorSystem
import no.finn.repoindexer.actors.StashProjectsActor
import com.typesafe.config.ConfigFactory

object ApplicationMain extends App {

  val config = ConfigFactory.load()
  val system = ActorSystem("RepoIndexer")
  val baseUrl = config.getString("stash.url")

  val projects = system.actorOf(StashProjectsActor.props, "projectsIndexer")

  projects ! GetProjects(baseUrl)

  system.awaitTermination()
}
