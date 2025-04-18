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

package org.beangle.doc.html

import org.beangle.doc.html.Dom.Body

class Document extends DomNode {
  var styleSheets: StyleSheets = new StyleSheets(Seq.empty)

  var images: Map[String, Array[Byte]] = Map.empty

  override def name: String = "html"

  override def outerHtml: String = {
    val buf = new StringBuilder("""<!DOCTYPE html><html lang="zh_CN">""".stripMargin)
    buf.append("\n")
    if (styleSheets.styles.nonEmpty) {
      buf.append("  <head>\n    <style>\n")
      buf.append(styleSheets.styles.map(_.toString(4)).mkString("\n"))
      buf.append("\n    </style>\n  </head>\n")
    }
    val body = this.childNodes.find(_.name == "body")
    if (body.nonEmpty) {
      appendXml(body.head, buf)
    }
    buf.append("</html>")
    buf.toString
  }

  def body: Body = {
    childNodes.find(_.name == "body") match
      case None =>
        val body = new Body
        append(body)
        body
      case Some(body) => body.asInstanceOf[Body]
  }

  def newImage(name: String, data: Array[Byte]): Unit = {
    images = images.updated(name, data)
  }
}
