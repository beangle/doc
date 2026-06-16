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

import org.beangle.doc.pdf.Logger

import java.time.Duration
import java.util.concurrent.{CountDownLatch, TimeUnit}

/** One browser tab connected over CDP WebSocket. */
class ChromePage(val idx: Int, val pageId: String, val socketUrl: String) {
  private val socket = WebSocket(socketUrl)
  private var frameId: String = _
  private var loadLatch: CountDownLatch = _
  private var enabled: Boolean = _

  /** Max wait after load for async requests to finish (dynamic pages). */
  private val NetworkIdleTimeout = Duration.ofSeconds(30)

  def enable(): Unit = {
    if !enabled then
      socket.send("Page.enable")
      socket.invoke("Page.setLifecycleEventsEnabled", Map("enabled" -> true))
      enabled = true
  }

  /** Wait for loadEventFired, then networkIdle, before returning. Handlers are per-navigation. */
  def navigate(url: String): String = {
    if null != loadLatch then loadLatch.await()
    loadLatch = new CountDownLatch(1)
    var loaded = false
    var acceptEvents = false

    socket.addHandler("Page.loadEventFired", () => {
      //println(s"Get a Page.loadEventFired ${url}")
      if acceptEvents then loaded = true
    })
    socket.addParamHandler("Page.lifecycleEvent", params => {
      if acceptEvents && loaded && (params \ "name").toString == "networkIdle" && null != loadLatch then
        //println(s"Get a Page.lifecycleEvent ${url}")
        loadLatch.countDown()
    })

    try {
      acceptEvents = true
      // Loopback aliases are rewritten to data: URLs to avoid Chrome LNA navigation blocks.
      val r = socket.invoke("Page.navigate", Map("url" -> LoopbackUrl.navigateUrl(url)))
      if r.isOk then frameId = (r.result \ "frameId").toString
      else frameId = null

      if r.isOk then
        loadLatch.await(NetworkIdleTimeout.toSeconds, TimeUnit.SECONDS)
      //println(s"Page.navigate ${url}")
      frameId
    } finally {
      socket.removeHandlers("Page.loadEventFired", "Page.lifecycleEvent")
      loadLatch = null
    }
  }

  /** Calls Page.printToPDF; renderDelay is an extra settle time after navigation. */
  def printToPDF(params: Map[String, Any], renderDelay: Duration): (String, String) = {
    if (null == frameId) {
      ("", "Cannot open page")
    } else {
      if !renderDelay.isZero then Thread.sleep(renderDelay.toMillis)
      val r = socket.invoke("Page.printToPDF", params)
      if r.isOk then ((r.result \ "data").toString, "") else ("", r.error)
    }
  }

  def close(): Unit = {
    try
      socket.close()
    catch
      case e: Throwable => Logger.debug(s"Close page ${pageId} ignored: ${e.getMessage}")
  }

  override def toString: String = {
    s"id:${idx},socketUrl:${socketUrl}"
  }
}
