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

import org.beangle.commons.net.Networks
import org.beangle.commons.os.{LinuxBash, Platform, WinCmd}
import org.beangle.doc.pdf.PdfLogger
import org.jodconverter.local.office.LocalOfficeManager

import java.nio.file.Path
import java.time.Duration

object LibreOfficeLauncher {
  def apply(): LibreOfficeLauncher = {
    new LibreOfficeLauncher(new Configuration)
  }

  class Configuration {
    var pipeNames: Array[String] = Array.empty
    var processTimeout: Duration = Duration.ofMinutes(2) //2m
    var taskTimeout: Duration = Duration.ofMinutes(5) //5m
    var maxTaskPerProcess: Int = 50
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

class LibreOfficeLauncher(cfg: Configuration) {

  def launch(processCount: Int = 1): LocalOfficeManager = {
    require(processCount >= 1 && processCount <= 20, "processCount should great than 0 and less than 20")
    val path = findSoffice().getOrElse(throw new RuntimeException("Cannot find LibreOffice execute file."))
    val home = path.getParent.getParent
    if (killOffice()) {
      PdfLogger.info("Autokill running libreoffice process")
    }
    val builder = LocalOfficeManager.builder.officeHome(home.toFile)
      .pipeNames(cfg.pipeNames: _*)
      .processTimeout(cfg.processTimeout.toMillis)
      .maxTasksPerProcess(cfg.maxTaskPerProcess)
      .taskExecutionTimeout(cfg.taskTimeout.toMillis)

    if (cfg.pipeNames.length < processCount) {
      builder.portNumbers(Networks.nextFreePorts(2002, processCount - cfg.pipeNames.length): _*)
    }

    val officeManager = builder.build()
    officeManager.start()
    officeManager
  }
}
