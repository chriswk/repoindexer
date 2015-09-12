package no.finn

import java.io.File

import org.json4s.JsonAST.{JArray, JField}
import org.json4s.{CustomSerializer, JObject}

package object repoindexer {
  case object GetProjects
  case class GetRepositories(projectKey: String, link: String)
  case class CloneRepo(sshClone: StashLink, slug: String)
  case class IndexRepo(path: File)
  case class IndexFile(file: File)
  case class Project(key: String, id: Long, name: String, public: Boolean, link: Link)
  case class Link(url: String, rel: String)
  case class ProjectResponse(values: List[Project])
  case class RepoResponse(values: List[StashRepo])
  case class StashLink(href: String, name: Option[String])
//  case class StashLinks(self: List[StashLink], `clone`: List[StashLink])
  case class StashRepo(slug: String, name: String, cloneUrl: String, links: Map[String, List[StashLink]])

  type Projects = Seq[Project]

}
