import org.beangle.parent.Dependencies._
import org.beangle.parent.Settings._

ThisBuild / organization := "org.beangle.doc"
ThisBuild / version := "0.0.7"

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
val beangle_commons_core = "org.beangle.commons" %% "beangle-commons-core" % "5.2.5"
val commonDeps = Seq(logback_classic, logback_core, beangle_commons_core, scalatest)

lazy val root = (project in file("."))
  .settings()
  .aggregate(docx,pdf)

lazy val docx = (project in file("docx"))
  .settings(
    name := "beangle-doc-docx",
    common,
    libraryDependencies ++= (commonDeps ++ Seq(poi_ooxml))
  )

lazy val pdf = (project in file("pdf"))
  .settings(
    name := "beangle-doc-pdf",
    common,
    libraryDependencies ++= (commonDeps ++ Seq(itextpdf,jna,bcprov_jdk15to18,bcpkix_jdk15to18))
  )

publish / skip := true
