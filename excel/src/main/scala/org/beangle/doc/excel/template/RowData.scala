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

import org.apache.poi.ss.usermodel.Row
import org.beangle.doc.excel.CellRef

import scala.collection.mutable

object RowData {
  def createRowData(sheetData: SheetData, row: Row): RowData = {
    if (row == null) return null
    val rowData = new RowData(row)
    rowData.sheetData = sheetData
    rowData.height = row.getHeight
    val numberOfCells = row.getLastCellNum
    for (cellIndex <- 0 until numberOfCells) {
      val cell = row.getCell(cellIndex)
      if (cell != null) {
        val cellData = CellData.createCellData(rowData, new CellRef(row.getSheet.getSheetName, row.getRowNum, cellIndex), cell)
        rowData.addCellData(cellData)
      }
      else rowData.addCellData(null)
    }
    rowData
  }
}

class RowData(val row: Row) extends Iterable[CellData] {
  var height: Int = 0
  var sheetData: SheetData = null
  val cellDatas = new mutable.ArrayBuffer[CellData]

  def getNumberOfCells: Int = cellDatas.size

  def getCellData(col: Int): Option[CellData] = if (col < cellDatas.size) Some(cellDatas(col)) else None

  protected def addCellData(cellData: CellData): Unit = {
    cellDatas.addOne(cellData)
  }

  override def iterator: Iterator[CellData] = cellDatas.iterator

}
