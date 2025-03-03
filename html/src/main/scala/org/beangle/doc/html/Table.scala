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
import org.beangle.doc.html.Table.*

import scala.collection.mutable

class Table extends DomNode {
  var caption: Option[Caption] = None
  var colGroup: Option[ColGroup] = None
  var thead: Option[THead] = None
  var tbodies: mutable.Buffer[TBody] = Collections.newBuffer[TBody]
  var widths: Array[Length] = _

  private def calcLayout(): Unit = {
    var headRows: Iterable[Row] = null
    thead match
      case None =>
        if (tbodies.size > 1 && tbodies.head.rows.nonEmpty) {
          headRows = tbodies.head.rows
        }
      case Some(head) =>
        if (head.rows.nonEmpty) {
          headRows = head.rows
        }
    if null == headRows then
      widths = Array.ofDim[Length](0)
    else
      val columns = headRows.head.cells.map(_.colspan).sum
      widths = Array.ofDim[Length](columns)
      val wholeWidth = computedStyle.width.getOrElse(Table.defaultWidth)

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
        col.computedStyle.width.foreach { w =>
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
  }

  override def renderStyle(sheets: StyleSheets): Unit = {
    val cs = Collections.newBuffer[DomNode]

    cs.addAll(caption)
    cs.addAll(colGroup)
    cs.addAll(thead)
    cs.addAll(tbodies)
    children = cs.toSeq
    calcLayout()
    super.renderStyle(sheets)
  }

  def newBody(): TBody = {
    val b = new TBody
    append(b)
    tbodies += b
    b
  }

  def newColGroup(): ColGroup = {
    val cg = new ColGroup
    this.colGroup = Some(cg)
    prepend(cg)
    cg
  }

}

object Table {

  val defaultWidth = Length(1140f, "px")

  class Caption(content: String) extends DomNode {
    append(Dom.Text(content))
  }

  class THead extends DomNode {
    def rows: Seq[Row] = children.asInstanceOf[Seq[Row]]

    override def renderStyle(sheets: StyleSheets): Unit = {
      super.renderStyle(sheets)
      if !this.computedStyle.has("font-weight") then this.addStyle("font-weight", "bold")
    }
  }

  class TBody extends DomNode {
    def rows: Seq[Row] = children.asInstanceOf[Seq[Row]]

    def newRow(): Row = {
      val r = new Row()
      append(r)
      r
    }
  }

  class Row extends DomNode {
    override def name: String = "tr"

    def cells: Seq[Cell] = children.asInstanceOf[Seq[Cell]]

    def newCell(): Cell = {
      val c = new Cell()
      append(c)
      c
    }
  }

  class Cell extends DomNode {

    def colspan: Short = {
      attributes.getOrElse("colspan", "1").toShort
    }

    def rowspan: Short = {
      attributes.getOrElse("rowspan", "1").toShort
    }

    override def name: String = "td"

  }

  class TheadCell extends Cell {
    override def name: String = "th"
  }

  class ColGroup extends DomNode {
    override def name: String = "colgroup"

    def cols: Seq[Col] = children.asInstanceOf[Seq[Col]]

    def newCol(): Col = {
      val c = new Col()
      append(c)
      c
    }
  }

  class Col extends DomNode {
    def span: Int = {
      attributes.getOrElse("span", "1").toInt
    }

    def width: Option[String] = {
      attributes.get("width")
    }

    override def nonVoid: Boolean = true
  }
}
