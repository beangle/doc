import org.beangle.parent.Dependencies.*
import org.beangle.parent.Settings.*

ThisBuild / organization := "org.beangle.doc"
ThisBuild / version := "0.3.4"

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

val beangle_commons = "org.beangle.commons" % "beangle-commons" % "5.6.16"
val beangle_model = "org.beangle.data" % "beangle-model" % "5.8.10"

val commonDeps = Seq(logback_classic % "test", beangle_commons, scalatest)
val websocket_api = "javax.websocket" % "javax.websocket-api" % "1.1"
val websocket_tyrus_client = "org.glassfish.tyrus" % "tyrus-container-grizzly-client" % "1.20"
val json4s = "org.json4s" % "json4s-native_3" % "4.1.0-M3"
val itext_kernel = "com.itextpdf" % "kernel" % "8.0.2"
val itext_bouncy_castle_adapter = "com.itextpdf" % "bouncy-castle-adapter" % "8.0.2"

lazy val root = (project in file("."))
  .settings()
  .aggregate(dbf, docx, pdf, excel, transfer)

lazy val dbf = (project in file("dbf"))
  .settings(
    name := "beangle-doc-dbf",
    common,
    libraryDependencies ++= commonDeps
  )

lazy val docx = (project in file("docx"))
  .settings(
    name := "beangle-doc-docx",
    common,
    libraryDependencies ++= (commonDeps ++ Seq(poi_ooxml))
  )

lazy val excel = (project in file("excel"))
  .settings(
    name := "beangle-doc-excel",
    common,
    libraryDependencies ++= (commonDeps ++ Seq(poi_ooxml)),
    libraryDependencies ++= Seq(jexl3, jcl_over_slf4j)
  )

lazy val pdf = (project in file("pdf"))
  .settings(
    name := "beangle-doc-pdf",
    common,
    libraryDependencies ++= commonDeps,
    libraryDependencies ++= Seq(itext_kernel, itext_bouncy_castle_adapter, jna),
    libraryDependencies ++= Seq(json4s, websocket_api, websocket_tyrus_client)
  )

lazy val transfer = (project in file("transfer"))
  .settings(
    name := "beangle-doc-transfer",
    common,
    libraryDependencies ++= commonDeps,
    libraryDependencies ++= Seq(beangle_model)
  ).dependsOn(excel)

publish / skip := true
