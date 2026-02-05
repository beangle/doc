import org.beangle.parent.Dependencies.*
import org.beangle.parent.Settings.*

ThisBuild / organization := "org.beangle.doc"
ThisBuild / version := "0.5.2"

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/beangle/doc"),
    "scm:git@github.com:beangle/doc.git"
  )
)

ThisBuild / developers := List(
  Developer(
    id = "chaostone",
    name = "Tihua Duan",
    email = "duantihua@gmail.com",
    url = url("http://github.com/duantihua")
  )
)

ThisBuild / description := "The Beangle Doc Library"
ThisBuild / homepage := Some(url("https://beangle.github.io/doc/index.html"))

val beangle_commons = "org.beangle.commons" % "beangle-commons" % "6.0.0"
val beangle_template = "org.beangle.template" % "beangle-template" % "0.2.4"

val commonDeps = Seq(slf4j, logback_classic % "test", beangle_commons, scalatest)
val websocket_tyrus_client = "org.glassfish.tyrus" % "tyrus-container-grizzly-client" % "2.2.1"

lazy val root = (project in file("."))
  .settings(common)
  .aggregate(html, docx, pdf, excel)

lazy val html = (project in file("html"))
  .settings(
    name := "beangle-doc-html",
    common,
    libraryDependencies ++= commonDeps
  )

lazy val docx = (project in file("docx"))
  .settings(
    name := "beangle-doc-docx",
    common,
    libraryDependencies ++= commonDeps,
    libraryDependencies ++= Seq(poi_ooxml, beangle_template, freemarker, log4j_to_slf4j)
  ).dependsOn(html)

lazy val excel = (project in file("excel"))
  .settings(
    name := "beangle-doc-excel",
    common,
    libraryDependencies ++= commonDeps,
    libraryDependencies ++= Seq(poi_ooxml, jexl3, log4j_to_slf4j)
  ).dependsOn(html)

lazy val pdf = (project in file("pdf"))
  .settings(
    name := "beangle-doc-pdf",
    common,
    Compile / mainClass := Some("org.beangle.doc.pdf.SPDConverter"),
    libraryDependencies ++= commonDeps,
    libraryDependencies ++= Seq(itext_kernel, itext_layout, itext_bouncy_castle_adapter, jna),
    libraryDependencies ++= Seq(websocket_tyrus_client, jodconverter_local % "optional", libreoffice % "optional")
  )
publish / skip := true
