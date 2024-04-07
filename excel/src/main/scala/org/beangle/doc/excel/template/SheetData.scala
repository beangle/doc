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

import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.beangle.doc.excel.CellRef

import java.util
import java.util.{Collections, Comparator, List}
import scala.collection.mutable

object SheetData {
  def createSheetData(sheet: Sheet, transformer: Transformer): SheetData = {
    val sheetData = new SheetData(sheet)
    sheetData.transformer = transformer
    val numberOfRows = sheet.getLastRowNum + 1
    var numberOfColumns = -1
    for (i <- 0 until numberOfRows) {
      val rowData = RowData.createRowData(sheetData, sheet.getRow(i))
      sheetData.addRowData(rowData) //rowdata may be null
      if (rowData != null && rowData.getNumberOfCells > numberOfColumns) numberOfColumns = rowData.getNumberOfCells
    }
    for (i <- 0 until sheet.getNumMergedRegions) {
      val region = sheet.getMergedRegion(i)
      sheetData.mergedRegions.addOne(region)
    }
    if (numberOfColumns > 0) {
      sheetData.columnWidth = new Array[Int](numberOfColumns)
      for (i <- 0 until numberOfColumns) {
        sheetData.columnWidth(i) = sheet.getColumnWidth(i)
      }
    }
    val sheetConditionalFormatting = sheet.getSheetConditionalFormatting
    for (i <- 0 until sheetConditionalFormatting.getNumConditionalFormattings) {
      val conditionalFormatting = sheetConditionalFormatting.getConditionalFormattingAt(i)
      val poiConditionalFormatting = new PoiConditionalFormatting(conditionalFormatting)
      sheetData.poiConditionalFormattings.addOne(poiConditionalFormatting)
    }
    sheetData
  }
}

class SheetData(val sheet: Sheet) extends Iterable[RowData] {
  protected var columnWidth: Array[Int] = null
  private val rowDatas = new mutable.ArrayBuffer[RowData]
  var transformer: Transformer = null

  def getNumberOfRows: Int = rowDatas.size

  def sheetName: String = sheet.getSheetName

  def getColumnWidth(col: Int): Int = columnWidth(col)

  def getRowData(row: Int): Option[RowData] = if (row < rowDatas.size) Option(rowDatas(row)) else None

  def getCellData(cellRef: CellRef): Option[CellData] = {
    if cellRef.row < rowDatas.size then
      rowDatas(cellRef.row).getCellData(cellRef.col)
    else None
  }

  def addRowData(rowData: RowData): Unit = {
    rowDatas.addOne(rowData)
  }

  override def iterator: Iterator[RowData] = rowDatas.iterator

  val mergedRegions = new mutable.ArrayBuffer[CellRangeAddress]
  private val poiConditionalFormattings = new mutable.ArrayBuffer[PoiConditionalFormatting]

  def updateConditionalFormatting(srcCellData: CellData, targetCell: Cell): Unit = {
    for (conditionalFormatting <- poiConditionalFormattings) {
      val ranges = conditionalFormatting.ranges
      for (range <- ranges) {
        if (range.isInRange(srcCellData.row, srcCellData.col)) {
          val newRange: CellRangeAddress = new CellRangeAddress(targetCell.getRowIndex, targetCell.getRowIndex, targetCell.getColumnIndex, targetCell.getColumnIndex)
          val targetSheet: Sheet = targetCell.getSheet
          val targetSheetConditionalFormatting: SheetConditionalFormatting = targetSheet.getSheetConditionalFormatting
          val sortedRules = conditionalFormatting.rules.sortBy(_.getPriority)
          for (rule <- sortedRules) {
            targetSheetConditionalFormatting.addConditionalFormatting(Array[CellRangeAddress](newRange), rule)
          }
        }
      }
    }
  }
}
