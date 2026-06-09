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
import org.beangle.commons.io.IOs
import org.beangle.commons.lang.Charsets

import java.net.{InetAddress, URI}

/** Chrome CDP blocks navigation to loopback aliases (e.g. local.example.com -> 127.0.0.1),
 * while rewriting the host to 127.0.0.1 breaks apps that redirect to their canonical HTTPS domain.
 */
object LoopbackUrl {

  def navigateUrl(url: String): String = {
    if isLoopbackAlias(url) then toDataUrl(url) else url
  }

  private def isLoopbackAlias(url: String): Boolean = {
    val host = URI.create(url).getHost
    if null == host || host == "127.0.0.1" || host == "[::1]" then false
    else
      try InetAddress.getByName(host).isLoopbackAddress
      catch
        case _: Exception => false
  }

  private def toDataUrl(url: String): String = {
    val html = IOs.readString(URI.create(url).toURL.openStream())
    "data:text/html;charset=utf-8;base64," + Base64.encode(html.getBytes(Charsets.UTF_8))
  }
}
