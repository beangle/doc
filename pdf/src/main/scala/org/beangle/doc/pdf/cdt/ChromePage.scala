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

import org.beangle.commons.codec.binary.Base64
import org.beangle.commons.lang.Charsets
import java.util.concurrent.CountDownLatch

class ChromePage(val idx: Int, val pageId: String, val socketUrl: String) {
  private val socket = WebSocket(socketUrl)
  private var frameId: String = _
  private var workingLatch: CountDownLatch = _
  private var enabled: Boolean = _

  def isWorking: Boolean = {
    workingLatch != null && workingLatch.getCount > 0
  }

  def enable(): Unit = {
    if !enabled then
      socket.send("Page.enable")
      enabled = true
  }

  def navigate(url: String): String = {
    if null != workingLatch then workingLatch.await()
    socket.addHandler("Page.frameStoppedLoading", () => if (null != workingLatch) workingLatch.countDown())
    workingLatch = new CountDownLatch(1)
    val r = socket.invoke("Page.navigate", Map("url" -> url))
    if r.isOk then frameId = (r.result \ "frameId").toString
    else frameId = null

    frameId
  }

  def printToPDF(params: Map[String, Any]): (String, String) = {
    if (null == frameId) {
      ("", "Cannot open page")
    } else {
      if (null != workingLatch) {
        workingLatch.await()
        workingLatch = new CountDownLatch(1)
        val r = socket.invoke("Page.printToPDF", params)
        workingLatch.countDown()
        workingLatch = null
        if r.isOk then ((r.result \ "data").toString, "") else ("", r.error)
      } else {
        ("", "Page is loading")
      }
    }
  }

  private def encodeBase64(msg: String): String = {
    Base64.encode(msg.getBytes(Charsets.UTF_8))
  }

  override def toString: String = {
    s"id:${idx},socketUrl:${socketUrl}"
  }
}
