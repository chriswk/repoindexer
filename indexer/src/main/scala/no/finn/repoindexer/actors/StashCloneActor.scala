package no.finn.repoindexer.actors

import java.io.File

import akka.actor.{Actor, ActorLogging}
import no.finn.repoindexer.CloneRepo
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.CredentialItem.YesNoType
import org.eclipse.jgit.transport.{UsernamePasswordCredentialsProvider, CredentialItem, CredentialsProvider, URIish}
import resource._

class StashCloneActor extends Actor with ActorLogging with StashActor {
  val localRepoFolder = config.getString("repo.folder")
  val usernamePasswordProvider = new UsernamePasswordCredentialsProvider(userName, password)
  def receive = {
    case CloneRepo(cloneUrl, slug) => {
      val localPath = new File(localRepoFolder, slug)
      localPath.mkdirs()
      for {
        repo <- managed(Git.cloneRepository().setRemote(cloneUrl).setDirectory(localPath).setCredentialsProvider(usernamePasswordProvider).call())
      } {
        log.info(s"Done cloning ${repo.getRepository.getDirectory}")
      }
    }
  }
}
