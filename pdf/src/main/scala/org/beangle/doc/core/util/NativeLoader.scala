/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.doc.core.util

import java.io.File

import com.sun.jna
import com.sun.jna.{Library, Platform}
import org.beangle.commons.io.{Dirs, Files}
import org.beangle.commons.lang.SystemInfo

class NativeLoader(groupId: String, artifactId: String) {
  private val ArtifactHome = groupId + "/" + groupId
  private val RepositoryHome = SystemInfo.user.home + "/.m2/repository"

  def load[T <: Library](path: String,  version: String, clazz: Class[T], libraryWrapper: LibraryWrapper[T]): T = {
    val dirs = Dirs.on(RepositoryHome).mkdirs(ArtifactHome)
    val versions = dirs.cd(ArtifactHome).ls()

    var dll: File = null
    if (versions.contains(version)) {
      dll = getBundleFile(version)
      if (!dll.exists()) {
        throw new RuntimeException(s"Cannot find $dll")
      }
    } else {
      if (version == "lastest") {
        val rs = versions.sorted.reverse find { v => getBundleFile(v).exists() }
        rs match {
          case None =>
            if (Platform.isWindows) {
              val evnDllPath = path + File.separator + artifactId + getSuffix
              val envDll = new File(evnDllPath)
              if (envDll.exists()) {
                val instance = jna.Native.load(envDll.getAbsolutePath, clazz)
                libraryWrapper.init(instance)
                val newVersion = libraryWrapper.version(instance)
                libraryWrapper.destroy(instance)
                dll = getBundleFile(newVersion)
                Files.copy(envDll, dll)
              } else {
                throw new RuntimeException(s"Cannot find $evnDllPath")
              }
            } else {
              throw new RuntimeException(s"Cannot find bundle in $RepositoryHome/$ArtifactHome ")
            }
          case Some(v) => dll = getBundleFile(v)
        }
      } else {
        throw new RuntimeException(s"Cannot find ${getBundleName(version)}")
      }
    }

    val instance = jna.Native.load(dll.getAbsolutePath, clazz)
    libraryWrapper.init(instance)
    instance
  }

  private def getBundleFile(version: String): File = {
    new File(RepositoryHome + "/" + ArtifactHome + "/" + version + "/" + getBundleName(version))
  }

  private def getSuffix: String = {
    (if (Platform.is64Bit) "" else ".32") +
      (if (Platform.isWindows) ".dll" else if (Platform.isMac) ".dylib" else ".so")
  }

  private def getBundleName(version: String): String = {
    artifactId +"-" + version + getSuffix
  }
}

trait LibraryWrapper[T <: Library] {

  def version(t: T): String

  def init(t: T): Unit

  def destroy(t: T): Unit
}
