package no.finn

package object repoindexer {
  case object GetProjects
  case class Project(name: String, slug: String)
  type Projects = Seq[Project]
}
