import sbt.Keys._
import sbt._

object BuildSettings {
  val buildScalaVersion = "3.0.1"

  val commonSettings = Seq(
    organizationName := "The Beangle Software",
    licenses += ("GNU Lesser General Public License version 3", new URL("http://www.gnu.org/licenses/lgpl-3.0.txt")),
    startYear := Some(2005),
    scalaVersion := buildScalaVersion,
    scalacOptions := Seq("-Xtarget:11", "-deprecation", "-feature"),
    crossPaths := true,

    publishMavenStyle := true,
    publishConfiguration := publishConfiguration.value.withOverwrite(true),
    publishM2Configuration := publishM2Configuration.value.withOverwrite(true),
    publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true),

    versionScheme := Some("early-semver"),
    pomIncludeRepository := { _ => false }, // Remove all additional repository other than Maven Central from POM
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
    })
}

object Dependencies {
  val logbackVer = "1.2.4"
  val scalatestVer = "3.2.9"
  val commonsVer = "5.2.5"

  val poiVer="4.1.0"
  val itextVer="5.5.13.1"
  val jnaVer="5.8.0"
  val bouncycastleVer="1.68"

  val scalatest = "org.scalatest" %% "scalatest" % scalatestVer % "test"
  val logbackClassic = "ch.qos.logback" % "logback-classic" % logbackVer % "test"
  val logbackCore = "ch.qos.logback" % "logback-core" % logbackVer % "test"
  val commonsCore = "org.beangle.commons" %% "beangle-commons-core" % commonsVer

  val poiOoxml = "org.apache.poi" % "poi-ooxml" % poiVer
  val itextpdf = "com.itextpdf" % "itextpdf" % itextVer
  val jna = "net.java.dev.jna" % "jna" % jnaVer
  //pdf encrypt
  val bcprov_jdk15to18 = "org.bouncycastle" % "bcprov-jdk15to18" % bouncycastleVer
  val bcpkix_jdk15to18 = "org.bouncycastle" % "bcpkix-jdk15to18" % bouncycastleVer

  val commonDeps = Seq(logbackClassic, logbackCore, commonsCore, scalatest)
}
