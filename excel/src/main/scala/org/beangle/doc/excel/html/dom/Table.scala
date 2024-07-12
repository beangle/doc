package org.beangle.doc.excel.html.dom

import org.beangle.commons.collection.Collections
import org.beangle.doc.excel.html.dom.Table.*

import scala.collection.mutable

class Table extends DomNode {
  var caption: Option[Caption] = None
  var colGroup: Option[ColGroup] = None
  var thead: Option[THead] = None
  var tbodies = Collections.newBuffer[TBody]
  var widths: Array[Length] = _

  def buildLayout(): Array[Length] = {
    var headRows: Iterable[Row] = null
    thead match
      case None =>
        if (tbodies.size > 1 && tbodies(0).rows.nonEmpty) {
          headRows = tbodies(0).rows
        }
      case Some(head) =>
        if (head.rows.nonEmpty) {
          headRows = head.rows
        }
    if null == headRows then 0
    else
      val columns = headRows.head.cells.map(_.colspan).sum
      widths = Array.ofDim[Length](columns)
      val wholeWidth = style match
        case Some(s) => s.width.getOrElse(Table.defaultWidth)
        case None => Table.defaultWidth

      colGroup foreach { group =>
        var i = 0
        group.cols foreach { col =>
          col.width foreach { cw =>
            val w = Length(cw)
            (i until i + col.span) foreach { ci =>
              var unit = w.unit
              var num = w.num / col.span
              if (unit == "%") {
                unit = "px"
                num = num / 100 * wholeWidth.num
              }
              if (i < widths.length) widths(i) = Length(num, unit)
            }
          }
          i += col.span
        }
      }
      val cells = headRows.head.cells
      var i = 0
      cells foreach { col =>
        col.style.flatMap(_.width).foreach { w =>
          (i until i + col.colspan) foreach { ci =>
            if (widths(ci) == null) {
              var unit = w.unit
              var num = w.num / col.colspan
              if (unit == "%") {
                unit = "px"
                num = num / 100 * wholeWidth.num
              }
              widths(i) = Length(num, unit)
            }
          }
        }
        i += col.colspan
      }
    this.widths
  }

  def name: String = "table"

  override def children: collection.Seq[DomNode] = {
    val children = Collections.newBuffer[DomNode]
    children.addAll(caption)
    children.addAll(colGroup)
    children.addAll(thead)
    children.addAll(tbodies)
    children
  }

  def toXml: String = {
    val buf = new StringBuilder("""<?xml version="1.0"?>""")
    buf.append("\n")
    appendXml(this, buf, 0)
    buf.toString
  }

}

object Table {

  val defaultWidth = Length(1140f, "px")

  class Caption(val content: Text) extends DomNode {
    def name: String = "caption"

    override def text: Option[Text] = Some(content)
  }

  class THead extends DomNode {
    val rows = Collections.newBuffer[Row]

    def name: String = "thead"

    override def children: collection.Seq[DomNode] = rows

    def add(row: Row): Unit = {
      row.parent = Some(this)
      rows += row
    }
  }

  class TBody extends DomNode {
    val rows = Collections.newBuffer[Row]

    def name: String = "tbody"

    override def children: collection.Seq[DomNode] = rows

    def add(row: Row): Unit = {
      row.parent = Some(this)
      rows += row
    }
  }

  class Row extends DomNode {
    val cells = Collections.newBuffer[Cell]

    def name: String = "tr"

    override def children: collection.Seq[DomNode] = cells

    def add(cell: Cell): Unit = {
      cell.parent = Some(this)
      cells += cell
    }
  }

  class Cell extends DomNode {
    var content: Text = _
    var colspan: Short = 1
    var rowspan: Short = 1

    override def name: String = "td"

    override def text: Option[Text] = Option(content)

    override def children: collection.Seq[DomNode] = Seq.empty

    override def attributes: Map[String, String] = {
      val props = Collections.newMap[String, String]
      if (colspan > 1) {
        props.put("colspan", colspan.toString)
      }
      if (rowspan > 1) {
        props.put("rowspan", rowspan.toString)
      }
      if style.nonEmpty then props.put("style", style.get.toString)
      props.toMap
    }
  }

  class TheadCell extends Cell {
    override def name: String = "th"
  }

  class ColGroup extends DomNode {
    val cols = Collections.newBuffer[Col]

    override def name: String = "colgroup"

    override def children: collection.Seq[DomNode] = cols

    def add(col: Col): Unit = {
      col.parent = Some(this)
      cols += col
    }

  }

  class Col extends DomNode {
    var width: Option[String] = None
    var span: Int = 1

    override def name: String = "col"

    override def attributes: Map[String, String] = {
      val props = Collections.newMap[String, String]
      width foreach { w =>
        props.put("width", w)
      }
      if (span > 1) {
        props.put("span", span.toString)
      }
      if style.nonEmpty then props.put("style", style.get.toString)
      props.toMap
    }
  }
}
