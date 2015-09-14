package no.finn.repoindexer.actors

import java.io.File

import akka.actor.{Props, Actor, ActorLogging}
import no.finn.repoindexer.{IndexFile, IndexRepo}

import scala.annotation.tailrec

class ProjectReaderActor extends Actor with ActorLogging {
  val elasticsearchActor = context.actorOf(Props[ElasticSearchActor])

  private val includeExtensions = Seq(".java", ".scala", ".xml")
  private def shouldIndex(file: File) = includeExtensions.exists(extension => file.getPath.endsWith(extension))

  def receive = {
    case IndexRepo(path, repoName) =>
      log.info("Indexing repo at "+path)
      val files = listFiles(path)
      files.collect {
        case file if shouldIndex(file) =>
          elasticsearchActor ! IndexFile(file, repoName)
      }
  }

  // From StackOverflow http://stackoverflow.com/questions/2637643/how-do-i-list-all-files-in-a-subdirectory-in-scala
  private def listFiles(file: File): List[File] = {
    @tailrec
    def listFiles(files: List[File], result: List[File]): List[File] = files match {
      case Nil => result
      case head :: tail if head.isDirectory =>
        listFiles(Option(head.listFiles).map(_.toList ::: tail).getOrElse(tail), result)
      case head :: tail if head.isFile =>
        listFiles(tail, head :: result)
    }
    listFiles(List(file), Nil)
  }
}
