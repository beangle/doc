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

package org.beangle.doc.excel.template.directive

import org.beangle.doc.excel.{CellRef, Size}
import org.beangle.doc.excel.template.{Area, Context}

/**
 * <p>Merge cells</p>
 * <pre>jx:mergeCells(
 * lastCell="Merge cell ranges"
 * [, cols="Number of columns combined"]
 * [, rows="Number of rows combined"]
 * [, minCols="Minimum number of columns to merge"]
 * [, minRows="Minimum number of rows to merge"]
 * )</pre>
 */
class MergeCellsDirective(area: Area) extends AbstractDirective {
  var cols = null
  var rows = null
  var minCols = null
  var minRows = null

  addArea(area)

  override def applyAt(cellRef: CellRef, context: Context): Size = {
    val area = areas.head
    var rows = getVal(this.rows, context)
    var cols = getVal(this.cols, context)
    rows = Math.max(getVal(this.minRows, context), rows)
    cols = Math.max(getVal(this.minCols, context), cols)
    rows = if (rows > 0) rows else area.size.height
    cols = if (cols > 0) cols else area.size.width
    if (rows > 1 || cols > 1) area.transformer.mergeCells(cellRef, rows, cols)
    area.applyAt(cellRef, context)
    new Size(cols, rows)
  }

  private def getVal(expression: String, context: Context): Int = {
    if (expression != null && expression.trim.length > 0) {
      val obj = context.evaluator.eval(expression, context.toMap)
      try return obj.toString.toInt
      catch {
        case e: NumberFormatException =>
          throw new IllegalArgumentException("Expression: " + expression + " failed to resolve")
      }
    }
    0
  }
}
