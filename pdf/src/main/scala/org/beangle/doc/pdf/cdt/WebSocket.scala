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

import jakarta.websocket.*
import org.beangle.commons.collection.Collections
import org.beangle.commons.concurrent.Locks
import org.beangle.commons.json.Json
import org.beangle.commons.lang.Strings
import org.beangle.doc.pdf.Logger
import org.beangle.doc.pdf.cdt.WebSocket.Response
import org.glassfish.tyrus.client.ClientManager
import org.glassfish.tyrus.container.grizzly.client.GrizzlyClientContainer

import java.io.IOException
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

object WebSocket {

  private val client: ClientManager = ClientManager.createClient(classOf[GrizzlyClientContainer].getName)
  client.getProperties.put("org.glassfish.tyrus.incomingBufferSize", 32 * 1024 * 1024)

  def apply(wsUrl: String): WebSocket = {
    val socket = new WebSocket(URI.create(wsUrl))
    socket.connect()
    socket
  }

  case class Response(result: Json, error: String) {
    def isOk: Boolean = Strings.isBlank(error)
  }
}

class WebSocket(uri: URI) {

  private var session: Session = _

  private val sendLock = new ReentrantLock()

  private var invokeLatch: CountDownLatch = _

  private var res: Response = _

  private val handlers = Collections.newMap[String, () => Unit]

  private val commandId = new AtomicInteger(1)

  private var lastId: Int = 0

  def addHandler(event: String, handler: () => Unit): Unit = {
    handlers.put(event, handler)
  }

  def connect(): Unit = {
    val socket = this
    session = WebSocket.client.connectToServer(new Endpoint() {
      override def onOpen(session: Session, config: EndpointConfig): Unit = {
        Logger.debug(s"Connected ${session.getRequestURI}")
      }

      override def onClose(session: Session, closeReason: CloseReason): Unit = {
        Logger.debug(s"Connection closed ${closeReason.getReasonPhrase},${uri}")
        if (null != invokeLatch) {
          invokeLatch.countDown()
          res = Response(Json.emptyObject, closeReason.getReasonPhrase)
        }
        handlers.values foreach { f => f() }
      }

      override def onError(session: Session, thr: Throwable): Unit = {
        Logger.error("Error in web socket session.", thr)
        thr.printStackTrace()
      }
    }, uri)

    session.addMessageHandler(new MessageHandler.Whole[String]() {
      def onMessage(var1: String): Unit = {
        if (Logger.isDebugEnabled) {
          if var1.length > 5000 then Logger.debug("Receive message " + var1.substring(0, 40))
          else Logger.debug("Receive message " + var1)
        }
        val v = Json.parseObject(var1)
        if (v.contains("id")) {
          val id = v.getInt("id")
          if (id == lastId) {
            res = Response(v \ "result", v.getString("errorText"))
            if null != invokeLatch then invokeLatch.countDown()
          }
        } else if (v.contains("method")) {
          handlers.get(v.getString("method")) foreach { f => f() }
        } else {
          Logger.error("Ignore event " + var1)
        }
      }
    })
  }

  def send(method: String): Unit = {
    val id = commandId.getAndIncrement
    val msg = s"""{"id":${id},"method":"${method}"}"""
    Logger.debug("send message:" + msg)
    session.getBasicRemote.sendText(msg)
  }

  def invoke(method: String, params: Map[String, Any]): Response = {
    Locks.withLock(sendLock) {
      val id = commandId.getAndIncrement
      this.lastId = id
      val paramStr = params.map { case (k, v) =>
        val s = v match
          case sv: String => s"\"$v\""
          case _ => v.toString
        s""""$k":$s"""
      }.mkString(",")

      val message = s"""{"id":${id},"method":"${method}","params":{${paramStr}}}"""
      Logger.debug("send message:" + message)
      try {
        res = null
        session.getBasicRemote.sendText(message)
        invokeLatch = new CountDownLatch(1)
        invokeLatch.await()
      } catch {
        case e: Throwable =>
          Logger.error("invoke socket error", e)
          if null == res then res = Response(Json.emptyObject, e.getMessage)
      }
      if null == res then res = Response(Json.emptyObject, "")
      else if Strings.isNotBlank(res.error) then Logger.error(res.error)
      res
    }
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
