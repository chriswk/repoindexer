package no.finn

import java.io.File

import ammonite.ops.Path
import com.github.javaparser.ast.{PackageDeclaration, ImportDeclaration}
import com.github.javaparser.ast.body.TypeDeclaration
import no.finn.repoindexer.IdxProcess.IdxProcess
import org.json4s.JsonAST.{JArray, JField}
import org.json4s.{CustomSerializer, JObject}

package object repoindexer {
  case object GetProjects
  case class GetRepositories(projectKey: String, link: String)
  case class CloneRepo(sshClone: Option[StashLink], slug: String) {
    def escapedUrl(stashLink: StashLink): String = {
      stashLink.href.replaceAll("\\W", "-")
    }
  }
  case class IndexRepo(path: Path, slug: String, fullUrl: String)
  case class IndexFile(path: Path, slug: String, project: String)

  case class Project(key: String, id: Long, name: String, public: Boolean, link: Link)
  case class Link(url: String, rel: String)
  case class ProjectResponse(values: List[Project])
  case class RepoResponse(values: List[StashRepo])
  case class StashLink(href: String, name: Option[String])
  case class ProjectInfo(key: String, url: String)
//  case class StashLinks(self: List[StashLink], `clone`: List[StashLink])
  case class StashRepo(slug: String, name: String, cloneUrl: String, links: Map[String, List[StashLink]])
  case class JavaClassInfo(className: String, imports: List[ImportDeclaration] = List(), packageName: Option[PackageDeclaration] = None,
                           typeDeclarations :List[TypeDeclaration] = List(),
                           implements: Array[Class[_]], extendsClass: Class[_], methods: Array[String])
  case class IndexCandidate(fileType: IdxProcess, slug: String, project: String, path: Path, content: String = "", javaClass: Option[JavaClassInfo] = None)

  type Projects = Seq[Project]

  object IdxProcess extends Enumeration {
    type IdxProcess = Value
    val JAVA, OTHER = Value
  }

}
