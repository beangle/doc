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

import java.util.regex.Pattern
import scala.xml.Node

/** HTML网页table解析
 */
object TableParser {

  private def parseAttributes(elem: Node, node: DomNode): Unit = {
    val props = Collections.newMap[String, String]
    elem.attributes foreach { n =>
      val v = n.value.toString()
      if Strings.isNotEmpty(v) then props.put(n.key.toLowerCase, v.trim)
    }
    node.attributes = props.toMap
  }

  private def find(searchString: String, regex: String): Seq[String] = {
    val pattern = Pattern.compile(regex)
    val matcher = pattern.matcher(searchString)
    val results = Collections.newBuffer[String]
    while (matcher.find()) {
      results.addOne(searchString.substring(matcher.start(), matcher.end))
    }
    results.toSeq
  }

  def parse(html: String): Document = {
    var t = html
    val sheets = find(t, "(?ims)<style>(.*)</style>")
    val classStyles = Collections.newBuffer[ClassStyle]
    sheets.foreach { s =>
      val ss = Strings.substringBetween(s, "<style>", "</style>")
      t = Strings.replace(t, s, "")
      classStyles.addAll(StyleSheets.parse(ss))
    }

    val cols = find(t, "(?i)<col (.*?)[^/]>")
    cols foreach { col =>
      t = Strings.replace(t, col, col.substring(0, col.length - 1) + "/>")
    }

    val document = new Document

    val headStr = find(t, "(?ims)<head(.*)</head>").headOption.getOrElse("<head></head>")
    val headXml = scala.xml.XML.loadString("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + headStr)

    (headXml \\ "title") foreach { title =>
      document.updateTitle(title.text)
    }
    var bodyStr = find(t, "(?ims)<body(.*)</body>").head
    bodyStr = Strings.replace(bodyStr, "<br>", "<br/>")
    bodyStr = Strings.replace(bodyStr, "&nbsp;", "&amp;nbsp;")
    val contents = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + bodyStr

    val xml = scala.xml.XML.loadString(contents)
    document.styleSheets = new StyleSheets(classStyles.toSeq)

    val body = new Dom.Body
    document.append(body)
    parseAttributes(xml, body)


    (xml \ "table") foreach { tab =>
      val table = new Table
      val colGroup = new Table.ColGroup
      table.colGroup = Some(colGroup)
      body.append(table)

      parseAttributes(tab, table)
      (tab \ "colgroup" \ "col") foreach { elem =>
        val col = new Table.Col
        parseAttributes(elem, col)
        colGroup.append(col)
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
        head.append(row)
        parseAttributes(tr, row)
        (tr \ "th") foreach { td =>
          val cell = new Table.TheadCell
          row.append(cell)
          cell.append(Dom.Text(readText(td)))
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
          body.append(row)
          parseAttributes(tr, row)
          (tr \ "td") foreach { td =>
            val cell = new Table.Cell
            row.append(cell)
            cell.append(Dom.Text(readText(td)))
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

  /** 从HTML的表示读成值
   *
   * @param node
   * @return
   */
  private def readText(node: Node): String = {
    var text = node.child.map { c =>
      val cstr = c.text
      if cstr.isEmpty then
        val outerhtml = c.toString
        if outerhtml.startsWith("<br") || outerhtml.startsWith("<p") then "<br/>" else ""
      else
        cstr
    }.mkString

    text = Strings.replace(text, "\r", "")
    text = text.replaceAll("\\s*\\n\\s*", "") //去除换行之后，再将<br/>还原
    text = Strings.replace(text, "<br/>", "\n")
    text = Strings.replace(text, "&nbsp;", " ")
    text = Strings.replace(text, "&amp;nbsp;", " ")
    text.trim()
  }
}
