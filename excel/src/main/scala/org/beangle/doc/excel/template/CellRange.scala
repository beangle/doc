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

class CellRange(var startCellRef: CellRef, var width: Int, var height: Int) {
  val sheetName: String = startCellRef.sheetName
  private var cells = new Array[Array[CellRef]](height)
  private var changeMatrix = new Array[Array[Boolean]](height)
  private var colHeights = new Array[Int](width)
  private var rowWidths = new Array[Int](height)
  var cellShiftStrategy:CellShiftStrategy = CellShiftStrategy.Inner

  for (row <- 0 until height) {
    rowWidths(row) = width
    cells(row) = new Array[CellRef](width)
    changeMatrix(row) = new Array[Boolean](width)
    for (col <- 0 until width) {
      cells(row)(col) = new CellRef(sheetName, row, col)
    }
  }
  for (col <- 0 until width) {
    colHeights(col) = height
  }

  def getCell(row: Int, col: Int): CellRef = cells(row)(col)

  private def setCell(row: Int, col: Int, cellRef: CellRef): Unit = {
    cells(row)(col) = cellRef
  }

  def shiftCellsWithRowBlock(startRow: Int, endRow: Int, col: Int, colShift: Int, updateRowWidths: Boolean): Unit = {
    for (i <- 0 until height; j <- 0 until width) {
      val requiresShifting = cellShiftStrategy.requiresColShifting(cells(i)(j), startRow, endRow, col)
      if (requiresShifting && isHorizontalShiftAllowed(col, colShift, i, j)) {
        cells(i)(j).col = cells(i)(j).col + colShift
        changeMatrix(i)(j) = true
      }
    }
    if (updateRowWidths) {
      val maxRow = Math.min(endRow, rowWidths.length - 1)
      for (row <- startRow to maxRow) {
        rowWidths(row) += colShift
      }
    }
  }

  private def isHorizontalShiftAllowed(col: Int, widthChange: Int, cellRow: Int, cellCol: Int): Boolean = {
    if (changeMatrix(cellRow)(cellCol)) return false
    if (widthChange >= 0) return true
    for (i <- cellCol - 1 until col by -1) {
      if (isEmpty(cellRow, i)) return false
    }
    true
  }

  def requiresColShifting(cell: CellRef, startRow: Int, endRow: Int, startColShift: Int): Boolean = {
    cellShiftStrategy.requiresColShifting(cell, startRow, endRow, startColShift)
  }

  def shiftCellsWithColBlock(startCol: Int, endCol: Int, row: Int, rowShift: Int, updateColHeights: Boolean): Unit = {
    for (i <- 0 until height; j <- 0 until width) {
      val requiresShifting = cellShiftStrategy.requiresRowShifting(cells(i)(j), startCol, endCol, row)
      if (requiresShifting && isVerticalShiftAllowed(row, rowShift, i, j)) {
        cells(i)(j).row = cells(i)(j).row + rowShift
        changeMatrix(i)(j) = true
      }
    }
    if (updateColHeights) {
      val maxCol = Math.min(endCol, colHeights.length - 1)
      for (col <- startCol to maxCol) {
        colHeights(col) += rowShift
      }
    }
  }

  private def isVerticalShiftAllowed(row: Int, heightChange: Int, cellRow: Int, cellCol: Int): Boolean = {
    if (changeMatrix(cellRow)(cellCol)) return false
    if (heightChange >= 0) return true
    for (i <- cellRow - 1 until row by -1) {
      if (isEmpty(i, cellCol)) return false
    }
    true
  }

  def excludeCells(startCol: Int, endCol: Int, startRow: Int, endRow: Int): Unit = {
    for (row <- startRow to endRow; col <- startCol to endCol) {
      cells(row)(col) = null
    }
  }

  def clearCells(startCol: Int, endCol: Int, startRow: Int, endRow: Int): Unit = {
    for (row <- startRow to endRow) {
      for (col <- startCol to endCol) {
        cells(row)(col) = CellRef.NONE
      }
    }
  }

  def calculateHeight: Int = {
    var maxHeight = 0
    for (col <- 0 until width) {
      maxHeight = Math.max(maxHeight, colHeights(col))
    }
    maxHeight
  }

  def calculateWidth: Int = {
    var maxWidth = 0
    for (row <- 0 until height) {
      maxWidth = Math.max(maxWidth, rowWidths(row))
    }
    maxWidth
  }

  def isExcluded(row: Int, col: Int): Boolean = cells(row)(col) == null || CellRef.NONE.equals(cells(row)(col))

  def contains(row: Int, col: Int): Boolean = row >= 0 && row < cells.length && col >= 0 && cells(0).length > col

  def containsDirectivesInRow(row: Int): Boolean = {
    for (col <- 0 until width) {
      if (cells(row)(col) == null || (cells(row)(col) eq CellRef.NONE)) return true
    }
    false
  }

  def isEmpty(row: Int, col: Int): Boolean = cells(row)(col) == null

  def hasChanged(row: Int, col: Int): Boolean = changeMatrix(row)(col)

  def resetChangeMatrix(): Unit = {
    for (i <- 0 until height; j <- 0 until width) {
      changeMatrix(i)(j) = false
    }
  }

  def findTarrow(srcRow: Int): Int = {
    var maxRow = -1
    for (col <- 0 until width) {
      val cellRef = cells(srcRow)(col)
      maxRow = Math.max(maxRow, cellRef.row)
    }
    if (maxRow < 0) maxRow = srcRow
    maxRow
  }
}
