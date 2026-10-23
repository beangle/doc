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

package org.beangle.doc.office

import com.sun.star.bridge.UnoUrlResolver
import com.sun.star.comp.helper.Bootstrap
import com.sun.star.uno.{UnoRuntime, XComponentContext}
import org.beangle.commons.net.Networks
import org.beangle.commons.os.{LinuxBash, Platform, WinCmd}

import java.net.{InetSocketAddress, Socket}
import java.nio.file.{Files, Path}
import java.time.Duration
import java.util.Comparator
import java.util.concurrent.TimeUnit

object LibreOfficeLauncher {

  class Configuration {
    /** 0 means pick a free port starting from 2002. */
    var port: Int = 0
    var connectTimeout: Duration = Duration.ofSeconds(60)
  }

  def findSoffice(): Option[Path] = {
    if (Platform.isLinux) {
      LinuxBash.find("soffice")
    } else if (Platform.isWin) {
      WinCmd.find("soffice.exe")
    } else {
      throw new RuntimeException(s"${Platform.osName} is not supported in LibreOffice detection.")
    }
  }

  def killOffice(): Boolean = {
    if (Platform.isLinux) {
      LinuxBash.killall("soffice.bin") > 0
    } else if (Platform.isWin) {
      WinCmd.killall("soffice.bin") > 0
    } else {
      throw new RuntimeException(s"${Platform.osName} is not supported in LibreOffice detection.")
    }
  }
}

import org.beangle.doc.office.LibreOfficeLauncher.*

/** Starts a single headless soffice process and connects to it over UNO (socket). */
class LibreOfficeLauncher(cfg: Configuration) {

  private var process: Option[Process] = None
  private var profileDir: Option[Path] = None
  private var context: Option[XComponentContext] = None

  /** Start soffice and return the remote UNO component context. */
  def launch(): XComponentContext = {
    val path = findSoffice().getOrElse(throw new RuntimeException("Cannot find LibreOffice execute file."))
    killOffice()
    val port = if (cfg.port > 0) cfg.port else Networks.nextFreePorts(2002, 1).head
    val profile = Files.createTempDirectory("beangle-doc-office")
    profileDir = Some(profile)

    val accept = s"socket,host=127.0.0.1,port=$port;urp;StarOffice.ComponentContext"
    val cmd = List(
      path.toString,
      "--headless", "--nologo", "--nofirststartwizard", "--norestore",
      s"--accept=$accept",
      s"-env:UserInstallation=${profile.toUri}"
    )
    val builder = new ProcessBuilder(cmd*)
    builder.redirectErrorStream(true)
    builder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
    val proc = builder.start()
    process = Some(proc)

    if (!waitForPort(port, cfg.connectTimeout)) {
      shutdown()
      throw new RuntimeException(s"LibreOffice did not open port $port within ${cfg.connectTimeout}")
    }
    try {
      val remote = connect(port, cfg.connectTimeout)
      context = Some(remote)
      remote
    } catch {
      case e: RuntimeException =>
        shutdown()
        throw e
    }
  }

  /** Terminate the office process and clean up the temporary profile. */
  def shutdown(): Unit = {
    context.foreach { ctx =>
      try {
        val desktop = ctx.getServiceManager.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)
        val xDesktop = UnoRuntime.queryInterface(classOf[com.sun.star.frame.XDesktop], desktop)
        xDesktop.terminate()
      } catch {
        case _: Throwable => // fall through to process kill
      }
    }
    context = None

    process.foreach { proc =>
      try {
        if (!proc.waitFor(5, TimeUnit.SECONDS)) {
          proc.destroyForcibly()
          proc.waitFor(5, TimeUnit.SECONDS)
          killOffice()
        }
      } catch {
        case _: InterruptedException =>
          proc.destroyForcibly()
          Thread.currentThread().interrupt()
      }
    }
    process = None

    profileDir.foreach { dir =>
      try {
        Files.walk(dir).sorted(Comparator.reverseOrder[Path]()).forEach(p => p.toFile.delete())
      } catch {
        case _: Throwable => // best-effort cleanup
      }
    }
    profileDir = None
  }

  private def waitForPort(port: Int, timeout: Duration): Boolean = {
    val deadline = System.currentTimeMillis() + timeout.toMillis
    while (System.currentTimeMillis() < deadline) {
      try {
        val socket = new Socket()
        try {
          socket.connect(new InetSocketAddress("127.0.0.1", port), 500)
          return true
        } finally {
          socket.close()
        }
      } catch {
        case _: Throwable => Thread.sleep(200)
      }
    }
    false
  }

  private def connect(port: Int, timeout: Duration): XComponentContext = {
    val local = Bootstrap.createInitialComponentContext(null)
    val resolver = UnoUrlResolver.create(local)
    val url = s"uno:socket,host=127.0.0.1,port=$port;urp;StarOffice.ComponentContext"
    val deadline = System.currentTimeMillis() + timeout.toMillis
    var last: Exception = null
    while (System.currentTimeMillis() < deadline) {
      try {
        return UnoRuntime.queryInterface(classOf[XComponentContext], resolver.resolve(url))
      } catch {
        case e: Exception =>
          last = e
          Thread.sleep(250)
      }
    }
    throw new RuntimeException(s"Cannot connect to LibreOffice on port $port", last)
  }
}
