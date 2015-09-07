package no.finn.repoindexer

case class GetProjects()
case class GetRepositories(projectKey: String, link: String)
case class CloneRepo(cloneUrl: String)

