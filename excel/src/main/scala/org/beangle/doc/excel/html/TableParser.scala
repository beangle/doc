package org.beangle.doc.excel.html

import org.beangle.commons.lang.Strings
import org.beangle.doc.excel.html.dom.{DomNode, Style, Table, Text}

import scala.xml.Node

object TableParser {

  private def parseStyle(elem: Node, node: DomNode): Unit = {
    val styleElems = elem \ "@style"
    val styles =
      if styleElems.isEmpty then Map.empty
      else
        val elem = styleElems.head
        val texts = Strings.split(elem.text, ";")
        texts.map { text =>
          val key = Strings.substringBefore(text, ":")
          val value = Strings.substringAfter(text, ":")
          (key.trim, value.trim)
        }.toMap
    if (styles.nonEmpty) {
      node.style = Some(dom.Style(styles))
    }
  }

  def parse(html: String): Table = {
    var t = html
    t = Strings.replace(t, "<br>", "<br/>")
    t = Strings.replace(t, "&nbsp;", "&amp;nbsp;")
    val contents = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + t

    val table = new Table
    val colGroup = new Table.ColGroup
    table.colGroup = Some(colGroup)

    val xml = scala.xml.XML.loadString(contents)
    parseStyle(xml, table)
    (xml \ "colgroup" \ "col") foreach { elem =>
      val col = new Table.Col
      (elem \ "@width") foreach { w =>
        col.width = Some(w.text)
      }
      (elem \ "@span") foreach { s =>
        col.span = s.text.toInt
      }
      colGroup.add(col)
    }
    (xml \ "caption") foreach { elem =>
      val caption = new Table.Caption(Text(elem.text, None))
      table.caption = Some(caption)
      caption.parent = Some(table)
      parseStyle(elem, caption)
    }
    val head = new Table.THead
    (xml \ "thead" \ "tr") foreach { tr =>
      val row = new Table.Row
      head.add(row)
      parseStyle(tr, row)
      if (row.style.isEmpty || row.style.get.has("font-weight")) {
        row.addStyle("font-weight", "bold")
      }
      (tr \ "th") foreach { td =>
        val cell = new Table.TheadCell
        row.add(cell)
        cell.content = Text(td.child.map(_.toString).mkString.trim(), None)
        parseStyle(td, cell)
        (td \ "@colspan") foreach { colspan =>
          cell.colspan = colspan.text.toShort
        }
        (td \ "@rowspan") foreach { rowspan =>
          cell.rowspan = rowspan.text.toShort
        }
      }
    }
    if (head.rows.nonEmpty) {
      table.thead = Some(head)
      head.parent = Some(table)
    }
    (xml \ "tbody") foreach { tbody =>
      val body = new Table.TBody
      (tbody \ "tr") foreach { tr =>
        val row = new Table.Row
        body.add(row)
        parseStyle(tr, row)
        (tr \ "td") foreach { td =>
          val cell = new Table.Cell
          row.add(cell)
          var text = td.child.map(_.toString).mkString
          text = Strings.replace(text, "\r", "")
          text = text.replaceAll("\\s*\\n\\s*", "") //去除空格之后，再将&nbsp;还原
          text = Strings.replace(text, "&amp;nbsp;", " ").trim()

          cell.content = Text(text, None)
          parseStyle(td, cell)
          (td \ "@colspan") foreach { colspan =>
            cell.colspan = colspan.text.toShort
          }
          (td \ "@rowspan") foreach { rowspan =>
            cell.rowspan = rowspan.text.toShort
          }
        }
      }
      if (body.rows.nonEmpty) {
        table.tbodies.addOne(body)
        body.parent = Some(table)
      }
    }
    table
  }
}
