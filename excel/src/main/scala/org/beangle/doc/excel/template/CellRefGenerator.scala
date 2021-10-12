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

package org.beangle.doc.excel.template

import org.beangle.doc.excel.CellRef

trait CellRefGenerator {
  def generateCellRef(index: Int, context: Context): CellRef
}

class MultiSheetCellRefGenerator(val sheetNames: collection.Seq[String], val startCellRef: CellRef) extends CellRefGenerator {
  override def generateCellRef(index: Int, context: Context): CellRef = {
    var sheetName = if (index >= 0 && index < sheetNames.size) sheetNames(index) else null
    val builder = context.getVar("sheetNameBuilder") match {
      case builder: SheetNameBuilder => sheetName = builder.createSheetName(sheetName, index)
      case _ =>
    }
    if sheetName == null then null else new CellRef(sheetName, startCellRef.row, startCellRef.col)
  }
}
