/*
 * Copyright (C) 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.beangle.doc.pdf.cdt

import org.beangle.commons.lang.Processes
import org.beangle.commons.logging.Logging
import org.beangle.doc.pdf.cdt.ChromeLauncher.{Arguments, Configuration}

import java.io.IOException
import java.lang.ProcessBuilder.Redirect
import java.nio.file.{Files, Path, Paths}
import java.util
import java.util.regex.{Matcher, Pattern}
import scala.collection.mutable

object ChromeLauncher {
  def apply(): ChromeLauncher = {
    new ChromeLauncher(new Configuration)
  }

  def findChrome(): Option[Path] = {
    Processes.find("CHROME_PATH", Array("/usr/bin/chromium",
      "/usr/bin/chromium-browser",
      "/usr/bin/google-chrome",
      "/snap/bin/chromium",
      "/Applications/Chromium.app/Contents/MacOS/Chromium",
      "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
      "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
      "C:/Program Files/Google/Chrome/Application/chrome.exe"))
  }

  class Configuration {
    /** startup wait time in seconds. */
    var startupWaitTime = 60
    /** shutdown wait time in seconds. */
    var shutdownWaitTime = 60
    /** wait time for threads to stop. */
    var threadWaitTime = 5
  }

  def defaultsArgs(headless: Boolean = true): Arguments = {
    val v = new Arguments
    v.noFirstRun()
      .noDefaultBrowserCheck()
      .disableBackgroundNetworking()
      .disableBackgroundTimerThrottling()
      .disableClientSidePhishingDetection()
      .disableDefaultApps()
      .disableExtensions()
      .disableHangMonitor()
      .disablePopupBlocking()
      .disablePromptOnRepost()
      .disableSync()
      .disableTranslate()
      .metricsRecordingOnly()
      .safebrowsingDisableAutoUpdate()
      .incognito()
      .add("remote-debugging-port", "0")
      .add("remote-allow-origins", "*")
      .add("run-all-compositor-stages-before-draw", true)

    if headless then v.headless().disableGpu().hideScrollbars().muteAudio()
    v
  }

  class Arguments {

    private val args: mutable.Map[String, Any] = new mutable.HashMap[String, Any]

    def add(key: String, value: Any): Arguments = {
      args.put(key, value)
      this
    }

    def headless(v: Boolean = true): Arguments = add("headless", v)

    def remoteDebuggingPort(port: Int): Arguments = add("remote-debugging-port", port)

    def noDefaultBrowserCheck(noCheck: Boolean = true): Arguments = add("no-default-browser-check", noCheck)

    def noFirstRun(noFirstRun: Boolean = true): Arguments = add("no-first-run", noFirstRun)

    def userDataDir(dir: String): Arguments = {
      add("user-data-dir", dir)
    }

    def getUserDataDir(): Option[String] = {
      args.get("user-data-dir").map(_.toString)
    }

    def genTempUserDataDir(): String = {
      val tempDir = Files.createTempDirectory("cdt-user-data-dir").toAbsolutePath.toString
      userDataDir(tempDir)
      tempDir
    }

    /** 隐身模式
     *
     * @param i
     */
    def incognito(i: Boolean = true): Arguments = add("incognito", i)

    def disableGpu(disable: Boolean = true): Arguments = add("disable-gpu", disable)

    def hideScrollbars(hide: Boolean = true): Arguments = add("hide-scrollbars", hide)

    def muteAudio(mute: Boolean = true): Arguments = add("mute-audio", mute)

    def disableBackgroundNetworking(disable: Boolean = true): Arguments = add("disable-background-networking", disable)

    /** 是否禁用后台定时器
     *
     * @param disable
     * @return
     */
    def disableBackgroundTimerThrottling(disable: Boolean = true): Arguments = add("disable-background-timer-throttling", disable)

    /** 客户端网络仿冒检测
     *
     * @param disable
     * @return
     */
    def disableClientSidePhishingDetection(disable: Boolean = true): Arguments = add("disable-client-side-phishing-detection", disable)

    def disableDefaultApps(disable: Boolean = true): Arguments = add("disable-default-apps", disable)

    def disableExtensions(disable: Boolean = true): Arguments = add("disable-extensions", disable)

    def disableHangMonitor(disable: Boolean = true): Arguments = add("disable-hang-monitor", disable)

    def disablePopupBlocking(disable: Boolean = true): Arguments = add("disable-popup-blocking", disable)

    def disablePromptOnRepost(disable: Boolean = true): Arguments = add("disable-prompt-on-repost", disable)

    def disableSync(disable: Boolean = true): Arguments = add("disable-sync", disable)

    def disableTranslate(disable: Boolean = true): Arguments = add("disable-translate", disable)

    def metricsRecordingOnly(value: Boolean = true): Arguments = add("metrics-recording-only", value)

    def safebrowsingDisableAutoUpdate(disable: Boolean = true): Arguments = add("safebrowsing-disable-auto-update", disable)

    def enableLogging(enable: Boolean = true): Arguments = add("enable-logging", enable)

    def build(): Seq[String] = {
      val argList = new mutable.ArrayBuffer[String]
      args foreach { case (k, v) =>
        v match
          case null =>
          case b: Boolean => if b then argList.addOne(s"--${k}")
          case _ => argList.addOne(s"--$k=$v")
      }
      argList.toSeq
    }
  }
}

class ChromeLauncher(config: Configuration) extends Logging {
  private var chromeProcess: Process = _

  private var userDataDirPath: Path = _

  def launch(headless: Boolean): Chrome = {
    launch(ChromeLauncher.defaultsArgs(headless))
  }

  def launch(arguments: Arguments): Chrome = {
    ChromeLauncher.findChrome() match
      case Some(p) => launch(p, arguments)
      case None => throw new RuntimeException("Cannot find executive chrome")
  }

  def launch(chromeBinary: Path, arguments: Arguments): Chrome = {
    if (isAlive) throw new IllegalStateException("Chrome process has already been started started.")
    if (arguments.getUserDataDir().isEmpty) {
      val userDatDir = arguments.genTempUserDataDir()
      userDataDirPath = Paths.get(userDatDir)
    }
    try {
      chromeProcess = Processes.launch(chromeBinary.toString, arguments.build(), pb => pb.redirectErrorStream(true).redirectOutput(Redirect.PIPE))
      new Chrome(this, "localhost", waitForDevToolsPort(chromeProcess))
    } catch {
      case e: IOException => throw new RuntimeException("Failed starting chrome process.", e)
      case e: Exception =>
        close()
        throw e
    }
  }

  def isAlive: Boolean = chromeProcess != null && chromeProcess.isAlive

  private def waitForDevToolsPort(process: Process): Int = {
    val matcher = Processes.grep(process, Pattern.compile("^DevTools listening on ws:\\/\\/.+?:(\\d+)\\/"), config.startupWaitTime)
    matcher match
      case None => throw new RuntimeException("cannot find dev tools listening port")
      case Some(m) => m.group(1).toInt
  }

  def close(): Unit = {
    org.beangle.commons.io.Files.remove(userDataDirPath.toFile)
    Processes.close(chromeProcess, config.shutdownWaitTime)
  }
}
