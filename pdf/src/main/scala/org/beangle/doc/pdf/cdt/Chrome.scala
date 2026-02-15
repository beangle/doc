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

import org.beangle.commons.concurrent.Locks
import org.beangle.commons.io.IOs
import org.beangle.commons.json.Json
import org.beangle.commons.lang.Strings
import org.beangle.doc.pdf.Logger

import java.io.{IOException, InputStream}
import java.net.{HttpURLConnection, URI}
import java.text.MessageFormat
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

object Chrome {

  def start(headless: Boolean = true): Chrome = {
    val launcher = ChromeLauncher()
    val chrome = launcher.launch(headless)
    Logger.debug(chrome.version())
    chrome
  }
}

/** Chrome DevTools Client
 * It can be used for *instrumenting, inspecting, debuging and profiling Chromium, Chrome and other Blink-based browsers.*
 * For more information on DevTools, see https://chromedevtools.github.io/devtools-protocol/.
 * Some ideas come from Github project chrome-devtools-java-client.
 *
 * @param launcher
 * @param host
 * @param port
 * @param maxIdle
 */
class Chrome(launcher: ChromeLauncher, host: String, port: Int, maxIdle: Int = 2) {

  private val freePages = new java.util.concurrent.ArrayBlockingQueue[ChromePage](maxIdle)

  private val pageIdGenerator = new AtomicInteger(1)

  private val lock = new ReentrantLock()

  private def nextPageIndex(): Int = pageIdGenerator.getAndIncrement()

  collectPages()

  def open(url: String): ChromePage = {
    val p = findOrCreatePage(url)
    p.navigate(url)
    p
  }

  def close(p: ChromePage): Unit = {
    if (!freePages.offer(p)) {
      closePage(p.pageId)
    }
  }

  private def collectPages(): Unit = {
    pages() foreach { p =>
      p.enable()
      freePages.add(p)
    }
  }

  private def findOrCreatePage(url: String): ChromePage = {
    Locks.withLock(lock) {
      if (freePages.isEmpty) {
        val p = createPage(url)
        p.enable()
        p
      } else {
        freePages.poll()
      }
    }
  }

  private def closePage(id: String): ChromePage = {
    request(classOf[ChromePage], "GET", String.format("http://%s:%d/%s/%s", host, port, "json/close", id))
  }

  private def createPage(url: String = ""): ChromePage = {
    request(classOf[ChromePage], "PUT", String.format("http://%s:%d/%s?%s", host, port, "json/new",
      if (Strings.isEmpty(url)) "about:blank" else url))
  }

  private def pages(): Array[ChromePage] = {
    request(classOf[Array[ChromePage]], "GET", String.format("http://%s:%d/%s", host, port, "json/list"))
  }

  def version(): String = {
    request(classOf[String], "GET", String.format("http://%s:%d/%s", host, port, "json/version"))
  }

  def exit(): Unit = {
    launcher.close()
  }

  private def request[T](responseType: Class[T], method: String, path: String): T = {
    var connection: HttpURLConnection = null
    var inputStream: InputStream = null
    try {
      val uri = URI.create(path).toURL
      connection = uri.openConnection.asInstanceOf[HttpURLConnection]
      connection.setRequestMethod(method)
      val responseCode = connection.getResponseCode
      if (HttpURLConnection.HTTP_OK == responseCode) {
        if (classOf[Void] == responseType) return null.asInstanceOf[T]
        inputStream = connection.getInputStream

        val res = IOs.readString(inputStream)
        val v = Json.parse(res)
        if (responseType == classOf[ChromePage]) {
          toPage(v).asInstanceOf[T]
        } else if (responseType.isArray && responseType.getComponentType == classOf[ChromePage]) {
          v.children.filter(x => (x \ "type").toString == "page").map(x => toPage(x)).toArray.asInstanceOf[T]
        } else if (responseType == classOf[String]) {
          res.asInstanceOf[T]
        } else {
          null.asInstanceOf[T]
        }
      } else {
        inputStream = connection.getErrorStream
        val responseBody = IOs.readString(inputStream)
        val message = MessageFormat.format("Server responded with non-200 code: {0} - {1}. {2}", responseCode, connection.getResponseMessage, responseBody)
        throw new RuntimeException(message)
      }
    } catch {
      case ex: IOException => throw new RuntimeException("Failed sending HTTP request.", ex)
    } finally {
      if (inputStream != null) try inputStream.close()
      catch
        case e: IOException =>
      if (connection != null) connection.disconnect()
    }
  }

  private def toPage(v: Json): ChromePage = {
    ChromePage(nextPageIndex(), (v \ "id").toString, (v \ "webSocketDebuggerUrl").toString)
  }
}
