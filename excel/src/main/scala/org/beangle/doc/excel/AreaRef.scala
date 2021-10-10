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

package org.beangle.doc.excel

import org.apache.poi.ss.SpreadsheetVersion
import org.apache.poi.ss.util.AreaReference
import org.beangle.commons.lang.Objects
import org.beangle.doc.excel.{AreaRef, Size}

object AreaRef {
  def ensureSameSheet(c1: CellRef, c2: CellRef): Unit = {
    assert((c1.sheetName == null && c2.sheetName == null) ||
      (c1.sheetName != null && c1.sheetName.equalsIgnoreCase(c2.sheetName)), "need same sheet")
  }

  def apply(cellRef: CellRef, size: Size): AreaRef = {
    val last = new CellRef(cellRef.sheetName, cellRef.row + size.height - 1, cellRef.col + size.width - 1)
    new AreaRef(cellRef, last)
  }

  def apply(areaRef: String): AreaRef = {
    val ar = new AreaReference(areaRef, SpreadsheetVersion.EXCEL2007)
    new AreaRef(CellRef(ar.getFirstCell), CellRef(ar.getLastCell))
  }

}

class AreaRef(val firstCellRef: CellRef, val lastCellRef: CellRef) {
  private val startRow = firstCellRef.row
  private val startCol = firstCellRef.col
  private val endRow = lastCellRef.row
  private val endCol = lastCellRef.col

  def sheetName: String = firstCellRef.sheetName

  def size: Size = {
    if (firstCellRef == null || lastCellRef == null) return Size.Zero
    new Size(endCol - startCol + 1, endRow - startRow + 1)
  }

  def contains(cellRef: CellRef): Boolean = {
    val sn = sheetName
    val otherSheetName = cellRef.sheetName
    if (sn == null && otherSheetName == null) || (sn != null && sn.equalsIgnoreCase(otherSheetName)) then
      cellRef.row >= startRow && cellRef.col >= startCol && cellRef.row <= endRow && cellRef.col <= endCol
    else false
  }

  def contains(row: Int, col: Int): Boolean = row >= startRow && row <= endRow && col >= startCol && col <= endCol

  def contains(areaRef: AreaRef): Boolean = {
    if (areaRef == null) return true
    if (sheetName == null && areaRef.sheetName == null) || (sheetName != null && sheetName.equalsIgnoreCase(areaRef.sheetName)) then
      contains(areaRef.firstCellRef) && contains(areaRef.lastCellRef)
    else false
  }

  override def toString: String = firstCellRef.toString + ":" + lastCellRef.getCellName(true)

  override def equals(o: Any): Boolean = {
    o match {
      case null => false
      case areaRef: AreaRef =>
        if (this eq areaRef) true
        else Objects.equals(firstCellRef, areaRef.firstCellRef) && Objects.equals(lastCellRef, areaRef.lastCellRef)
      case _ => false
    }
  }

  override def hashCode: Int = {
    var result = if (firstCellRef != null) firstCellRef.hashCode else 0
    result = 31 * result + (if (lastCellRef != null) lastCellRef.hashCode else 0)
    result
  }
}
