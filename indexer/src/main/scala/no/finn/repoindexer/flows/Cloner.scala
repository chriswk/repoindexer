package no.finn.repoindexer.flows

import java.io.File

import com.jcraft.jsch.{Session, JSch}
import com.typesafe.config.ConfigFactory
import no.finn.repoindexer.{IndexRepo, CloneRepo}
import org.eclipse.jgit.api.{Git, TransportConfigCallback}
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.TextProgressMonitor
import org.eclipse.jgit.transport.{SshTransport, Transport, OpenSshConfig, JschConfigSessionFactory}
import org.eclipse.jgit.util.FS
import org.json4s.DefaultFormats
import akka.stream.scaladsl._
import resource._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


object Cloner {
  val config = ConfigFactory.load()
  val privateKeyPath = config.getString("bamboo.privatekey")
  val privateKeyPass = config.getString("bamboo.privatepass")
  val localRepoFolder = config.getString("repo.folder")

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
  val progressMonitor = new TextProgressMonitor()

  val transportConfig = new TransportConfigCallback() {
    override def configure(transport: Transport): Unit = {
      val sshTransport = transport.asInstanceOf[SshTransport]
      sshTransport.setSshSessionFactory(sshSessionFactory)
    }
  }
  val sshSessionFactory = new CustomConfigSessionFactory()

  val clonerFlow : Flow[CloneRepo, IndexRepo, Unit] = Flow[CloneRepo].mapAsync(4) { repo =>
    repo.sshClone match {
      case Some(cloneUrl) => {
        val localPath = new File(localRepoFolder, repo.escapedUrl(cloneUrl))
        Future {
          if (localPath.exists) {
            println(s"Pulling ${repo}")
            val pullResult = new Git(new FileRepository(new File(localPath, ".git")))
              .pull()
              .setTransportConfigCallback(transportConfig)
              .setProgressMonitor(progressMonitor)
              .call()

          } else {
            localPath.mkdirs()
            val url = cloneUrl.href
            println(s"Cloning ${repo}")
            for {
              repo <- managed(Git
                .cloneRepository()
                .setURI(url)
                .setDirectory(localPath)
                .setTransportConfigCallback(transportConfig)
                .setProgressMonitor(progressMonitor)
                .call()
              )
            } {

            }
          }
          IndexRepo(localPath, repo.slug)
        }
      }
      case None => Future.failed(new IllegalStateException("No cloneurl"))
    }
  }
}
