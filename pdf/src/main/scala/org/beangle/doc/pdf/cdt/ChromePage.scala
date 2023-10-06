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
import org.json4s.*
import org.json4s.native.JsonMethods.*

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
      socket.send(s"""{"id":${idx}1,"method":"Page.enable"}""")
      enabled = true
  }

  def navigate(url: String): String = {
    if (null != workingLatch) {
      workingLatch.await()
      workingLatch = null
    }
    workingLatch = new CountDownLatch(1)
    val r = socket.invoke(s"""{"id":${idx}2,"method":"Page.navigate","params":{"url":"$url"}}""")
    if r.isOk then frameId = (r.result \ "frameId").values.toString
    else frameId = null

    socket.addHandler("Page.loadEventFired", () => if (null != workingLatch) workingLatch.countDown())
    frameId
  }

  def printToPDF(params: Map[String, Any]): String = {
    if (null == frameId) {
      encodeBase64("Cannot open page")
    } else {
      if (null != workingLatch) {
        workingLatch.await()
        workingLatch = new CountDownLatch(1)
        val paramStr = params.map { case (k, v) =>
          val s = v match
            case sv: String => s"\"$v\""
            case _ => v.toString
          s""""$k":$s"""
        }.mkString(",")
        val r = socket.invoke(s"""{"id":${idx}3,"method":"Page.printToPDF","params":{$paramStr}}""")
        workingLatch.countDown()
        workingLatch = null
        if r.isOk then (r.result \ "data").values.toString
        else encodeBase64(r.error)
      } else {
        encodeBase64("Page is loading")
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
