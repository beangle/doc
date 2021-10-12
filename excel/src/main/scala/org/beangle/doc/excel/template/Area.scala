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

import org.beangle.doc.excel.template.Area.*
import org.beangle.doc.excel.template.directive.Directive
import org.beangle.doc.excel.{AreaRef, CellRef, Size, template}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable

object Area {
  val Empty = new Area(new CellRef(null, 0, 0), Size.Zero, null)
  val logger = LoggerFactory.getLogger(classOf[Area])

  def apply(areaRef: AreaRef, transformer: Transformer): Area = {
    val startCell = areaRef.firstCellRef
    val endCell = areaRef.lastCellRef
    val size = new Size(endCell.col - startCell.col + 1, endCell.row - startCell.row + 1)
    new Area(startCell, size, transformer)
  }

  def apply(areaRef: String, transformer: Transformer): Area = {
    apply(AreaRef(areaRef), transformer)
  }

  def apply(startCell: CellRef, endCell: CellRef, transformer: Transformer): Area = {
    apply(new AreaRef(startCell, endCell), transformer)
  }
}

class Area(val startCellRef: CellRef, val size: Size, var transformer: Transformer) {
  var directiveDatas: List[DirectiveData] = List.empty
  var parentDirective: Directive = null
  private var cellRange: CellRange = null
  var cellsCleared: Boolean = false
  var formulaProcessor: FormulaProcessor = new DefaultFormulaProcessor
  // default cell shift strategy
  private var cellShiftStrategy: CellShiftStrategy = CellShiftStrategy.Inner

  def this(startCellRef: CellRef, size: Size, datas: List[DirectiveData], transformer: Transformer) = {
    this(startCellRef, size, transformer)
    this.directiveDatas = if (datas != null) datas else List.empty
  }

  def addDirective(areaRef: AreaRef, directive: Directive): Unit = {
    val thisAreaRef = AreaRef(startCellRef, size)
    if (!thisAreaRef.contains(areaRef))
      throw new IllegalArgumentException("Cannot add directive '" + directive.name + "' to area " + thisAreaRef + " at " + areaRef)
    directiveDatas :+= DirectiveData(areaRef, directive)
  }

  def addDirective(areaRef: String, directive: Directive): Unit = {
    directiveDatas :+= DirectiveData(areaRef, directive)
  }

  private def excludeCells(startCellRef: CellRef, size: Size): Unit = {
    cellRange.excludeCells(startCellRef.col - this.startCellRef.col, startCellRef.col - this.startCellRef.col + size.width - 1,
      startCellRef.row - this.startCellRef.row, startCellRef.row - this.startCellRef.row + size.height - 1)
  }

  private def createCellRange(): Unit = {
    cellRange = new CellRange(startCellRef, size.width, size.height)
    for (data <- directiveDatas) {
      val startCellRef = data.initStartCellRef
      val size = data.size
      if (data.directive.lockRange) excludeCells(startCellRef, size)
    }
  }

  def applyAt(cellRef: CellRef, context: Context): Size = {
    if (this == Area.Empty) return Size.Zero
    logger.debug("Applying Area at {}", cellRef)
    createCellRange()
    val topArea = transformTopStaticArea(cellRef, context)
    var tail = directiveDatas
    for (dd <- directiveDatas) {
      cellRange.resetChangeMatrix()
      tail = tail.tail
      val shiftMode = dd.directive.shiftMode
      cellRange.cellShiftStrategy = detectCellShiftStrategy(shiftMode)
      val cmdStartCellRef = dd.startCellRef
      val cmdInitialSize = dd.size
      val startCol = cmdStartCellRef.col - startCellRef.col
      val startRow = cmdStartCellRef.row - startCellRef.row
      val cmdNewSize = dd.directive.applyAt(cellRef.move(startRow, startCol), context)
      val widthChange = cmdNewSize.width - cmdInitialSize.width
      val heightChange = cmdNewSize.height - cmdInitialSize.height
      val endCol = startCol + cmdInitialSize.width - 1
      val endRow = startRow + cmdInitialSize.height - 1
      if (heightChange != 0) {
        cellRange.shiftCellsWithColBlock(startCol, endCol, endRow, heightChange, true)
        val cmdsToShift = findDirectivesForVerticalShift(tail, startCol, endCol, endRow, heightChange)

        for (cmdDataToShift <- cmdsToShift) {
          val cmdDataStartCellRef = cmdDataToShift.startCellRef
          val relativeRow = cmdDataStartCellRef.row - startCellRef.row
          val relativeStartCol = cmdDataStartCellRef.col - startCellRef.col
          val relativeEndCol = relativeStartCol + cmdDataToShift.size.width - 1
          cellRange.shiftCellsWithColBlock(relativeStartCol, relativeEndCol, relativeRow + cmdDataToShift.size.height - 1, heightChange, false)
          cmdDataToShift.startCellRef = new CellRef(cmdStartCellRef.sheetName, cmdDataStartCellRef.row + heightChange, cmdDataStartCellRef.col)
          if (heightChange < 0) {
            val initialStartCellRef = cmdDataToShift.initStartCellRef
            val initialSize = cmdDataToShift.size
            val initialStartRow = initialStartCellRef.row - startCellRef.row
            val initialEndRow = initialStartRow + initialSize.height - 1
            val initialStartCol = initialStartCellRef.col - startCellRef.col
            val initialEndCol = initialStartCol + initialSize.width - 1
            cellRange.clearCells(initialStartCol, initialEndCol, initialStartRow, initialEndRow)
          }
        }
      }
      if (widthChange != 0) {
        cellRange.shiftCellsWithRowBlock(startRow, endRow, endCol, widthChange, true)
        val cmdsToShift = findDirectivesForHorizontalShift(tail, startRow, endRow, endCol, widthChange)

        for (cmdDataToShift <- cmdsToShift) {
          val cmdDataStartCellRef = cmdDataToShift.startCellRef
          val relativeCol = cmdDataStartCellRef.col - startCellRef.col
          val relativeStartRow = cmdDataStartCellRef.row - startCellRef.row
          val relativeEndRow = relativeStartRow + cmdDataToShift.size.height - 1
          cellRange.shiftCellsWithRowBlock(relativeStartRow, relativeEndRow, relativeCol + cmdDataToShift.size.width - 1, widthChange, false)
          cmdDataToShift.startCellRef = new CellRef(cmdStartCellRef.sheetName, cmdDataStartCellRef.row, cmdDataStartCellRef.col + widthChange)
          if (widthChange < 0) {
            val initialStartCellRef = cmdDataToShift.initStartCellRef
            val initialSize = cmdDataToShift.size
            val initialStartRow = initialStartCellRef.row - startCellRef.row
            val initialEndRow = initialStartRow + initialSize.height - 1
            val initialStartCol = initialStartCellRef.col - startCellRef.col
            val initialEndCol = initialStartCellRef.col + initialSize.width - 1
            cellRange.clearCells(initialStartCol, initialEndCol, initialStartRow, initialEndRow)
          }
        }
      }
    }
    transformStaticCells(cellRef, context, topArea)
    val finalSize = new Size(cellRange.calculateWidth, cellRange.calculateHeight)
    updateCellDataFinalAreaForFormulaCells(AreaRef(cellRef, finalSize))
    directiveDatas foreach (_.resetStartCellAndSize())
    finalSize
  }

  private def transformStaticCells(cellRef: CellRef, context: Context, area: AreaRef): Unit = {
    var relativeStartRow = area.firstCellRef.row
    var relativeStartCol = area.firstCellRef.col + 1
    if (transformer.isForwardOnly) {
      relativeStartRow = area.lastCellRef.row + 1
      relativeStartCol = 0
    }
    transformStaticCells(cellRef, context, relativeStartRow, relativeStartCol)
  }

  private def findDirectivesForHorizontalShift(directiveDatas: List[DirectiveData], startRow: Int, endRow: Int, shiftingCol: Int, widthChange: Int): mutable.Set[DirectiveData] = {
    val result = new mutable.LinkedHashSet[DirectiveData]()
    var tail = directiveDatas
    for (dd <- directiveDatas; if !result.contains(dd)) {
      tail = tail.tail
      val ddStartCellRef = dd.startCellRef
      val relativeCol = ddStartCellRef.col - startCellRef.col
      val relativeStartRow = ddStartCellRef.row - startCellRef.row
      val relativeEndRow = relativeStartRow + dd.size.height - 1
      if (relativeCol > shiftingCol) {
        var isShiftingNeeded = false
        if (widthChange > 0) if ((relativeStartRow >= startRow && relativeStartRow <= endRow) || (relativeEndRow >= startRow && relativeEndRow <= endRow) || (startRow >= relativeStartRow && startRow <= relativeEndRow)) isShiftingNeeded = true
        else if (relativeStartRow >= startRow && relativeEndRow <= endRow && isNoHighDirectivesInArea(directiveDatas, shiftingCol + 1, relativeCol - 1, startRow, endRow)) isShiftingNeeded = true
        if (isShiftingNeeded) {
          result.add(dd)
          val dependents = findDirectivesForHorizontalShift(tail, relativeStartRow, relativeEndRow, relativeCol + dd.size.width - 1, widthChange)
          result.addAll(dependents)
        }
      }
    }
    result
  }

  private def findDirectivesForVerticalShift(directiveDatas: List[DirectiveData], startCol: Int, endCol: Int, shiftingRow: Int, heightChange: Int): mutable.Set[DirectiveData] = {
    val result = new mutable.LinkedHashSet[DirectiveData]()
    var tail = directiveDatas
    for (dd <- directiveDatas; if !result.contains(dd)) {
      tail = tail.tail
      val ddStartCellRef = dd.startCellRef
      val relativeRow = ddStartCellRef.row - startCellRef.row
      val relativeStartCol = ddStartCellRef.col - startCellRef.col
      val relativeEndCol = relativeStartCol + dd.size.width - 1
      if (relativeRow > shiftingRow) {
        var isShiftingNeeded = false
        if (heightChange > 0) if ((relativeStartCol >= startCol && relativeStartCol <= endCol) || (relativeEndCol >= startCol && relativeEndCol <= endCol) || (startCol >= relativeStartCol && startCol <= relativeEndCol)) isShiftingNeeded = true
        else if (relativeStartCol >= startCol && relativeEndCol <= endCol && isNoWideDirectivesInArea(directiveDatas, startCol, endCol, shiftingRow + 1, relativeRow - 1)) isShiftingNeeded = true
        if (isShiftingNeeded) {
          result.add(dd)
          val dependents = findDirectivesForVerticalShift(tail, relativeStartCol, relativeEndCol, relativeRow + dd.size.height - 1, heightChange)
          result.addAll(dependents)
        }
      }
    }
    result
  }

  private def isNoHighDirectivesInArea(directiveDatas: List[DirectiveData], startCol: Int, endCol: Int, startRow: Int, endRow: Int): Boolean = {
    !directiveDatas.exists { dd =>
      val ddStartCellRef = dd.startCellRef
      val relativeCol = ddStartCellRef.col - startCellRef.col
      val relativeEndCol = relativeCol + dd.size.width - 1
      val relativeStartRow = ddStartCellRef.row - startCellRef.row
      val relativeEndRow = relativeStartRow + dd.size.height - 1
      relativeCol >= startCol && relativeEndCol <= endCol &&
        ((relativeStartRow < startRow && relativeEndRow >= startRow) || (relativeEndRow > endRow && relativeStartRow <= endRow))
    }
  }

  private def isNoWideDirectivesInArea(directiveDatas: collection.Seq[DirectiveData], startCol: Int, endCol: Int, startRow: Int, endRow: Int): Boolean = {
    !directiveDatas.exists { dd =>
      val ddStartCellRef = dd.startCellRef
      val relativeRow = ddStartCellRef.row - startCellRef.row
      val relativeEndRow = relativeRow + dd.size.height - 1
      val relativeStartCol = ddStartCellRef.col - startCellRef.col
      val relativeEndCol = relativeStartCol + dd.size.width - 1
      relativeRow >= startRow && relativeEndRow <= endRow &&
        ((relativeStartCol < startCol && relativeEndCol >= startCol) || (relativeEndCol > endCol && relativeStartCol <= endCol))
    }
  }

  private def detectCellShiftStrategy(shiftMode: String): CellShiftStrategy = {
    if (shiftMode != null && Directive.ADJACENT_SHIFT_MODE.equalsIgnoreCase(shiftMode)) CellShiftStrategy.Adjacent
    else CellShiftStrategy.Inner
  }

  private def updateCellDataFinalAreaForFormulaCells(newAreaRef: AreaRef): Unit = {
    val sheetName = startCellRef.sheetName
    val offsetRow = startCellRef.row
    val startCol = startCellRef.col
    for (col <- 0 until size.width; row <- 0 until size.height) {
      if (!cellRange.isExcluded(row, col)) {
        val srcCell = new CellRef(sheetName, offsetRow + row, startCol + col)
        transformer.getCellData(srcCell) foreach { cellData =>
          if (cellData.isFormulaCell) cellData.addTargetParentAreaRef(newAreaRef)
        }
      }
    }
  }

  private def transformTopStaticArea(cellRef: CellRef, context: Context): AreaRef = {
    val topLeftCmdCell = findRelativeTopCmdCellRef
    val bottomRightCmdCell = findRelativeBottomCmdCellRef
    val topStaticAreaLastRow = topLeftCmdCell.row - 1
    for (col <- 0 until size.width; row <- 0 to topStaticAreaLastRow) {
      transformStaticCell(cellRef, context, row, col)
    }
    // update static cells before the directive cell for the first top left directive
    for (col <- 0 until topLeftCmdCell.col) {
      if (cellRange.contains(topLeftCmdCell.row, col)) transformStaticCell(cellRef, context, topLeftCmdCell.row, col)
    }
    if (parentDirective == null) updateRowHeights(cellRef, 0, topStaticAreaLastRow)
    new AreaRef(topLeftCmdCell, bottomRightCmdCell)
  }

  private def transformStaticCell(cellRef: CellRef, context: Context, row: Int, col: Int): Unit = {
    if (!cellRange.isExcluded(row, col)) {
      val relativeCell = cellRange.getCell(row, col)
      val srcCell = startCellRef.move(row, col)
      val targetCell = cellRef.move(relativeCell.row, relativeCell.col)
      try {
        updateCellDataArea(srcCell, targetCell, context)
        transformer.transform(srcCell, targetCell, context, parentDirective != null)
      } catch {
        case e: Exception => logger.error("Failed to transform " + srcCell + " into " + targetCell, e)
      }
    }
  }

  private def updateRowHeights(areaStartCellRef: CellRef, relativeStartRow: Int, relativeEndRow: Int): Unit = {
    if (transformer != null) {
      for (relativeSrcRow <- relativeStartRow to relativeEndRow) {
        if (!cellRange.containsDirectivesInRow(relativeSrcRow)) {
          val relativeTarrow = cellRange.findTarrow(relativeSrcRow)
          val tarrow = areaStartCellRef.row + relativeTarrow
          val srcRow = areaStartCellRef.row + relativeSrcRow
          try transformer.updateRowHeight(startCellRef.sheetName, srcRow, areaStartCellRef.sheetName, tarrow)
          catch {
            case e: Exception =>
              logger.error("Failed to update row height for src row={} and target row={}", relativeSrcRow, tarrow, e)
          }
        }
      }
    }
  }

  private def findRelativeTopCmdCellRef: CellRef = {
    var topCmdRow = startCellRef.row + size.height
    var topCmdCol = startCellRef.col + size.width

    for (data <- directiveDatas) {
      if (data.startCellRef.row <= topCmdRow && data.startCellRef.col <= topCmdCol) {
        topCmdRow = data.startCellRef.row
        topCmdCol = data.startCellRef.col
      }
    }
    CellRef(topCmdRow - startCellRef.row, topCmdCol - startCellRef.col)
  }

  private def findRelativeBottomCmdCellRef: CellRef = {
    var bottomCmdRow = startCellRef.row
    var bottomCmdCol = startCellRef.col

    for (data <- directiveDatas) {
      if (data.startCellRef.row + data.size.height >= bottomCmdRow) {
        bottomCmdRow = data.startCellRef.row + data.size.height - 1
        bottomCmdCol = data.startCellRef.col + data.size.width - 1
      }
    }
    CellRef(bottomCmdRow - startCellRef.row, bottomCmdCol - startCellRef.col)
  }

  def clearCells(): Unit = {
    if (cellsCleared) return
    val sheetName = startCellRef.sheetName
    val startRow = startCellRef.row
    val startCol = startCellRef.col
    for (row <- 0 until size.height; col <- 0 until size.width) {
      val cellRef: CellRef = new CellRef(sheetName, startRow + row, startCol + col)
      transformer.clearCell(cellRef)
    }
    transformer.resetArea(getAreaRef)
    cellsCleared = true
  }

  private def transformStaticCells(cellRef: CellRef, context: Context, relativeStartRow: Int, relativeStartCol: Int): Unit = {
    val sheetName = startCellRef.sheetName
    val offsetRow = startCellRef.row
    val startCol = startCellRef.col
    for (col <- 0 until size.width) {
      for (row <- relativeStartRow until size.height; if !(row == relativeStartRow && col < relativeStartCol)) {
        if (!cellRange.isExcluded(row, col)) {
          val relativeCell = cellRange.getCell(row, col)
          val srcCell = startCellRef.move(row, col)
          val targetCell = cellRef.move(relativeCell.row, relativeCell.col)
          try {
            updateCellDataArea(srcCell, targetCell, context)
            transformer.transform(srcCell, targetCell, context, parentDirective != null)
          } catch {
            case e: Exception => logger.error("Failed to transform " + srcCell + " into " + targetCell, e)
          }
        }
      }
    }
    if (parentDirective == null) updateRowHeights(cellRef, relativeStartRow, size.height - 1)
  }

  private def updateCellDataArea(srcCell: CellRef, targetCell: CellRef, context: Context): Unit = {
    transformer.getCellData(srcCell) foreach { cellData =>
      cellData.area = this
      cellData.addTargetPos(targetCell)
    }
  }

  def getAreaRef: AreaRef = AreaRef(startCellRef, size)

  def processFormulas(): Unit = {
    formulaProcessor.processAreaFormulas(transformer, this)
  }

  def findDirectiveByName(name: String): collection.Seq[Directive] = {
    directiveDatas.filter(x => name != null && name == x.directive.name).map(_.directive)
  }

  def reset(): Unit = {
    directiveDatas.foreach(_.reset())
    transformer.resetTargetCellRefs()
  }
}
