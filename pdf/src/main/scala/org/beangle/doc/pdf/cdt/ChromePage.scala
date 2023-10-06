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
