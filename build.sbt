import Dependencies._
import BuildSettings._
import sbt.url

ThisBuild / organization := "org.beangle.doc"
ThisBuild / version := "0.0.7-SNAPSHOT"

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/beangle/doc"),
    "scm:git@github.com:beangle/doc.git"
  )
)

ThisBuild / developers := List(
  Developer(
    id    = "chaostone",
    name  = "Tihua Duan",
    email = "duantihua@gmail.com",
    url   = url("http://github.com/duantihua")
  )
)

ThisBuild / description := "The Beangle Doc Library"
ThisBuild / homepage := Some(url("https://beangle.github.io/doc/index.html"))
ThisBuild / resolvers += Resolver.mavenLocal

lazy val root = (project in file("."))
  .settings()
  .aggregate(docx,pdf)

lazy val docx = (project in file("docx"))
  .settings(
    name := "beangle-doc-docx",
    commonSettings,
    libraryDependencies ++= (commonDeps ++ Seq(poiOoxml))
  )

lazy val pdf = (project in file("pdf"))
  .settings(
    name := "beangle-doc-pdf",
    commonSettings,
    libraryDependencies ++= (commonDeps ++ Seq(itextpdf,jna,bcprov_jdk15to18,bcpkix_jdk15to18))
  )

publish / skip := true
