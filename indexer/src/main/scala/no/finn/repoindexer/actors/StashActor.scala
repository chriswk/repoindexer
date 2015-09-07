package no.finn.repoindexer.actors

import akka.actor.Actor
import com.typesafe.config.ConfigFactory
import dispatch._, Defaults._

trait StashActor {
  this: Actor ⇒
  val config = ConfigFactory.load()
  val userName = config.getString("stash.username")
  val password = config.getString("stash.password")
  val baseUrl = url(config.getString("stash.url"))
  val apiPath = baseUrl / "rest" / "api" / "1.0"
}
