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

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.Strings
import org.beangle.commons.logging.Logging
import org.beangle.doc.pdf.cdt.WebSocket.Response
import org.glassfish.tyrus.client.ClientManager
import org.glassfish.tyrus.container.grizzly.client.GrizzlyClientContainer
import org.json4s.*
import org.json4s.native.JsonMethods.*

import java.io.IOException
import java.net.URI
import java.util.concurrent.CountDownLatch
import javax.websocket.*

object WebSocket {

  val client: ClientManager = ClientManager.createClient(classOf[GrizzlyClientContainer].getName)
  client.getProperties.put("org.glassfish.tyrus.incomingBufferSize", 8 * 1024 * 1024)

  def apply(wsUrl: String): WebSocket = {
    val socket = new WebSocket(URI.create(wsUrl))
    socket.connect()
    socket
  }

  case class Response(result: JValue, error: String) {
    def isOk: Boolean = Strings.isBlank(error)
  }
}

class WebSocket(uri: URI) extends Logging {

  private var session: Session = _

  private var invokeLatch: CountDownLatch = _

  private var res: Response = _

  private val handlers = Collections.newMap[String, () => Unit]

  def addHandler(event: String, handler: () => Unit): Unit = {
    handlers.put(event, handler)
  }

  def connect(): Unit = {
    val socket = this
    session = WebSocket.client.connectToServer(new Endpoint() {
      override def onOpen(session: Session, config: EndpointConfig): Unit = {
        logger.debug(s"Connected ${session.getRequestURI}")
      }

      override def onClose(session: Session, closeReason: CloseReason): Unit = {
        logger.debug(s"Connection closed ${closeReason.getCloseCode},${uri}")
        if (null != invokeLatch) invokeLatch.countDown()
        handlers.values foreach { f => f() }
      }

      override def onError(session: Session, thr: Throwable): Unit = {
        logger.error("Error in web socket session.", thr)
        thr.printStackTrace()
      }
    }, uri)

    session.addMessageHandler(new MessageHandler.Whole[String]() {
      def onMessage(var1: String): Unit = {
        val v = parse(var1)
        if ((v \ "id") != JNothing) {
          val errorText = (v \ "errorText").values match
            case None => ""
            case t: Any => t.toString
          res = Response(v \ "result", errorText)
          if (null != invokeLatch) invokeLatch.countDown()
        } else if ((v \ "method") != JNothing) {
          handlers.get((v \ "method").values.toString) foreach { f => f() }
        } else {
          logger.error("Ignore event " + var1)
        }
      }
    })
  }

  def send(message: String): Unit = {
    session.getBasicRemote.sendText(message)
  }

  def invoke(message: String): Response = {
    try {
      session.getBasicRemote.sendText(message)
      invokeLatch = new CountDownLatch(1)
      invokeLatch.await()
    } catch {
      case e: Throwable =>
        logger.error("invoke socket error", e)
        if null == res then res = Response(JNothing, e.getMessage)
    }
    if null == res then res = Response(JNothing, "")
    else if Strings.isNotBlank(res.error) then logger.error(res.error)
    res
  }

  def close(): Unit = {
    try {
      session.close()
      session = null
    } catch {
      case e: IOException =>
    }
  }
}
