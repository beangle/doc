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

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.Strings
import org.beangle.doc.html.dom.*

import scala.xml.Node

object HtmlParser {

  private def parseAttributes(elem: Node, node: DomNode): Unit = {
    val props = Collections.newMap[String, String]
    elem.attributes foreach { n =>
      val v = n.value.toString()
      if Strings.isNotEmpty(v) then props.put(n.key.toLowerCase, v.trim)
    }
    node.attributes = props.toMap
  }

  def parse(html: String): Document = {
    var t = html
    val sheets = ParseUtil.find(t, "(?ims)<style>(.*)</style>")
    val classStyles = Collections.newBuffer[ClassStyle]
    sheets.foreach { s =>
      val ss = Strings.substringBetween(s, "<style>", "</style>")
      t = Strings.replace(t, s, "")
      classStyles.addAll(ClassStyleParser.parse(ss))
    }

    val cols = ParseUtil.find(t, "(?i)<col (.*?)[^/]>")
    cols foreach { col =>
      t = Strings.replace(t, col, col.substring(0, col.length - 1) + "/>")
    }
    var bodyStr = ParseUtil.find(t, "(?ims)<body(.*)</body>").head
    bodyStr = Strings.replace(bodyStr, "<br>", "<br/>")
    bodyStr = Strings.replace(bodyStr, "&nbsp;", "&amp;nbsp;")
    val contents = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + bodyStr

    val xml = scala.xml.XML.loadString(contents)
    val document = new Document
    document.styleSheets = new StyleSheets(classStyles.toSeq)
    val body = new Body
    document.add(body)
    parseAttributes(xml, body)

    (xml \ "table") foreach { tab =>
      val table = new Table
      val colGroup = new Table.ColGroup
      table.colGroup = Some(colGroup)
      body.add(table)

      parseAttributes(tab, table)
      (tab \ "colgroup" \ "col") foreach { elem =>
        val col = new Table.Col
        parseAttributes(elem, col)
        colGroup.add(col)
      }
      (tab \ "caption") foreach { elem =>
        val caption = new Table.Caption(elem.text)
        table.caption = Some(caption)
        caption.parent = Some(table)
        parseAttributes(elem, caption)
      }
      val head = new Table.THead
      (tab \ "thead" \ "tr") foreach { tr =>
        val row = new Table.Row
        head.add(row)
        parseAttributes(tr, row)
        (tr \ "th") foreach { td =>
          val cell = new Table.TheadCell
          row.add(cell)
          cell.add(Text(readText(td)))
          parseAttributes(td, cell)
        }
      }
      if (head.rows.nonEmpty) {
        table.thead = Some(head)
        head.parent = Some(table)
      }
      (tab \ "tbody") foreach { tbody =>
        val body = new Table.TBody
        (tbody \ "tr") foreach { tr =>
          val row = new Table.Row
          body.add(row)
          parseAttributes(tr, row)
          (tr \ "td") foreach { td =>
            val cell = new Table.Cell
            row.add(cell)
            cell.add(Text(readText(td)))
            parseAttributes(td, cell)
          }
        }
        if (body.rows.nonEmpty) {
          table.tbodies.addOne(body)
          body.parent = Some(table)
        }
      }
    }
    body.render(document.styleSheets)
    document
  }

  private def readText(node: Node): String = {
    var text = node.child.map(_.toString).mkString
    text = Strings.replace(text, "\r", "")
    text = text.replaceAll("\\s*\\n\\s*", "") //去除空格之后，再将&nbsp;还原
    Strings.replace(text, "&amp;nbsp;", " ").trim()
  }
}
