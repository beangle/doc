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

import org.apache.poi.ss.usermodel.Workbook
import org.beangle.doc.excel.{AreaRef, CellRef, ImageType, Size}

import java.io.{IOException, OutputStream}
import scala.collection.mutable

/**
 * Defines interface methods for Excel operations
 */
trait Transformer {
  def workbook: Workbook

  def transform(srcCellRef: CellRef, targetCellRef: CellRef, context: Context, updateRowHeight: Boolean): Unit

  /** *
   * Writes Excel workbook to output stream but not close the stream
   * designed to use with ZipOutputStream or other OutputStream
   * for creates several xls files one time.
   */
  @throws[IOException]
  def writeTo(os: OutputStream): Unit

  /**
   * Writes Excel workbook to output stream and disposes the workbook.
   */
  @throws[IOException]
  def write(os: OutputStream): Unit

  def setFormula(cellRef: CellRef, formulaString: String): Unit

  def getFormulaCells: collection.Set[CellData]

  def getCellData(cellRef: CellRef): Option[CellData]

  def resetTargetCellRefs(): Unit

  def resetArea(areaRef: AreaRef): Unit

  def clearCell(cellRef: CellRef): Unit

  def getCommentedCells: collection.Seq[CellData]

  def updateRowHeight(srcSheetName: String, srcRowNum: Int, targetSheetName: String, targetRowNum: Int): Unit

  def adjustTableSize(ref: CellRef, size: Size): Unit

  def mergeCells(ref: CellRef, rows: Int, cols: Int): Unit

  def isForwardOnly: Boolean
}

abstract class AbstractTransformer extends Transformer {
  var ignoreColumnProps = false
  var ignoreRowProps = false
  var sheetMap: Map[String, SheetData]=_
  var evaluateFormulas = false
  var fullFormulaRecalculationOnOpening = false

  override def resetTargetCellRefs(): Unit = {
    for (sheetData <- sheetMap.values; i <- 0 until sheetData.getNumberOfRows) {
      sheetData.getRowData(i) foreach { rowData =>
        for (j <- 0 until rowData.getNumberOfCells) {
          rowData.getCellData(j) foreach { cd =>
            cd.resetTargetPos()
          }
        }
      }
    }
  }

  override def getCellData(cellRef: CellRef): Option[CellData] = {
    if (cellRef == null || cellRef.sheetName == null) return None
    sheetMap.get(cellRef.sheetName) match {
      case Some(sheetData) =>
        sheetData.getRowData(cellRef.row) match {
          case Some(rd) => rd.getCellData(cellRef.col)
          case None => None
        }
      case None => None
    }
  }

  override def getFormulaCells: collection.Set[CellData] = {
    val formulaCells = new mutable.HashSet[CellData]
    for (sheetData <- sheetMap.values; i <- 0 until sheetData.getNumberOfRows) {
      sheetData.getRowData(i) foreach { rowData =>
        for (j <- 0 until rowData.getNumberOfCells) {
          rowData.getCellData(j) foreach { cellData =>
            if (cellData.isFormulaCell) formulaCells.add(cellData)
          }
        }
      }
    }
    formulaCells
  }

  override def adjustTableSize(ref: CellRef, size: Size): Unit = {
  }

  override def isForwardOnly = false
}
