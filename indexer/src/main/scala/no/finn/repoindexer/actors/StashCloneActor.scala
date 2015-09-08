package no.finn.repoindexer.actors

import java.io.File

import akka.actor.{Actor, ActorLogging}
import com.jcraft.jsch.{JSch, Session}
import no.finn.repoindexer.CloneRepo
import org.eclipse.jgit.api.{TransportConfigCallback, Git}
import org.eclipse.jgit.transport.OpenSshConfig.Host
import org.eclipse.jgit.transport.{OpenSshConfig, Transport, JschConfigSessionFactory, SshTransport}
import org.eclipse.jgit.util.FS
import resource._

class StashCloneActor extends Actor with ActorLogging with StashActor {
  val localRepoFolder = config.getString("repo.folder")
  val privateKeyPath = config.getString("bamboo.privatekey")
  val privateKeyPass = config.getString("bamboo.privatepass")
  class CustomConfigSessionFactory extends JschConfigSessionFactory {
    override protected def getJSch(hc: OpenSshConfig.Host, fs: FS) : JSch = {
      val jsch = super.getJSch(hc, fs)
      jsch.removeAllIdentity()
      jsch.addIdentity(privateKeyPath, privateKeyPass)
      jsch
    }
    override protected def configure(hc: OpenSshConfig.Host, session: Session): Unit = {

    }
  }
  val sshSessionFactory = new CustomConfigSessionFactory()
  def receive = {
    case CloneRepo(cloneUrl, slug) => {
      val localPath = new File(localRepoFolder, slug)
      localPath.mkdirs()
      val url = cloneUrl.href
      for {
        repo <- managed(Git
          .cloneRepository()
          .setRemote(url)
          .setDirectory(localPath)
          .setTransportConfigCallback(new TransportConfigCallback() {
            override def configure(transport: Transport): Unit = {
             val sshTransport = transport.asInstanceOf[SshTransport]
             sshTransport.setSshSessionFactory(sshSessionFactory)
            }
          })
          .call()
        )
      } {
        log.info(s"Done cloning ${repo.getRepository.getDirectory}")
      }
    }
  }
}
