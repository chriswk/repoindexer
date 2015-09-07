package no.finn.repoindexer.actors

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestActorRef, TestKit}
import no.finn.repoindexer.GetProjects
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike}
import scala.concurrent.duration._

class StashProjectsActorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with FlatSpecLike
  with BeforeAndAfterAll
{
  def this() = this(ActorSystem("StashProjectsActorSpec"))

  override def afterAll() = system.shutdown(); system.awaitTermination(10.seconds)

  "A Stash Project Actor" should "ask for Stash projects" in {
    val actor = TestActorRef(Props[StashProjectsActor])
    actor ! GetProjects
    // TODO: test
  }
}
