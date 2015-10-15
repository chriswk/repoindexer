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
import scala.util.{Failure, Success, Try}


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

  val cloneFlow : Flow[CloneRepo, IndexRepo, Unit] = Flow[CloneRepo].mapAsyncUnordered(4) { repo =>
    repo.sshClone match {
      case Some(cloneUrl) => {
        val localPath = new File(localRepoFolder, repo.escapedUrl(cloneUrl))
        Future {
          if (localPath.exists) {
            val git = new Git(new FileRepository(new File(localPath, ".git")))
            println(s"Pulling ${repo}")
            try {
              git.pull()
                .setTransportConfigCallback(transportConfig)
                .setProgressMonitor(progressMonitor)
                .call()
            } catch {
              case e: Exception => println("Something went wrong while pulling")
            } finally {
              git.close()
            }
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
          IndexRepo(localPath, repo.slug, cloneUrl.href)
        }
      }
      case None => Future.failed(new IllegalStateException("No cloneurl"))
    }
  }

  val stashCloningFlow = Stash.cloneCandidatesFlow.via(cloneFlow)
}
