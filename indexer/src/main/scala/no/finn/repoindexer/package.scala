package no.finn

package object repoindexer {
  case object GetProjects
  case class GetRepositories(projectKey: String, link: String)
  case class CloneRepo(cloneUrl: String, slug: String)
  case class Project(key: String, id: Long, name: String, public: Boolean, link: Link)
  case class Link(url: String, rel: String)
  case class ProjectResponse(values: List[Project])
  case class RepoResponse(values: List[StashRepo])
  case class StashRepo(slug: String, name: String, cloneUrl: String)

  type Projects = Seq[Project]
}
