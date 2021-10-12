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
import org.beangle.doc.excel.template.FormulaProcessor.*

import java.util.regex.{Matcher, Pattern}
import scala.collection.mutable

object FormulaProcessor {
  val regexJointedLookBehind: String = "(?<!U_\\([^)]{0,100})"
  val regexSimpleCellRef = "[a-zA-Z]+[0-9]+"
  val regexCellRef = "([a-zA-Z_]+[a-zA-Z0-9_]*![a-zA-Z]+[0-9]+|(?<!\\d)[a-zA-Z]+[0-9]+|'[^?\\\\/:'*]+'![a-zA-Z]+[0-9]+)"
  val regexAreaRef = regexCellRef + ":" + regexSimpleCellRef
  val regexAreaRefPattern = Pattern.compile(regexAreaRef)
  val regexCellRefExcludingJointedPattern = Pattern.compile(regexJointedLookBehind + regexCellRef)
  val regexCellRefPattern = Pattern.compile(regexCellRef)
  val regexJointedCellRefPattern = Pattern.compile("U_\\([^\\)]+\\)")
  val regexExcludePrefixSymbols = "(?<!\\w)"

  /** Parses a formula and returns a list of cell names used in it
   * E.g. for formula "B4*(1+C4)" the returned list will contain "B4", "C4"
   */
  def getFormulaCellRefs(formula: String): List[String] = {
    getStringPartsByPattern(formula, regexCellRefExcludingJointedPattern)
  }

  /** Parses a formula to extract a list of so called "jointed cells"
   * The jointed cells are cells combined with a special notation "U_(cell1, cell2)" into a single cell
   * They are used in formulas like this "$[SUM(U_(F8,F13))]".
   * Here the formula will use both F8 and F13 source cells to calculate the sum
   */
  def getJointedCellRefs(formula: String): List[String] = {
    getStringPartsByPattern(formula, regexJointedCellRefPattern)
  }

  /** Parses a "jointed cell" reference and extracts individual cell references
   *
   */
  def getCellRefsFromJointedCellRef(jointedCellRef: String): List[String] = {
    getStringPartsByPattern(jointedCellRef, regexCellRefPattern)
  }

  def getStringPartsByPattern(str: String, pattern: Pattern): List[String] = {
    val cellRefs = new mutable.ArrayBuffer[String]
    if (str != null) {
      val cellRefMatcher = pattern.matcher(str)
      while (cellRefMatcher.find) cellRefs.addOne(cellRefMatcher.group)
    }
    cellRefs.toList
  }

  /** Checks if the formula contains jointed cell references
   * Jointed references have format U_(cell1, cell2) e.g. $[SUM(U_(F8,F13))]
   */
  def formulaContainsJointedCellRef(formula: String): Boolean = regexJointedCellRefPattern.matcher(formula).find

  /** Creates a list of target formula cell references
   */
  def createTargetCellRefListByColumn(targetFormulaCellRef: CellRef, targetCells: Iterable[CellRef], cellRefsToExclude: collection.Set[CellRef]): collection.Seq[CellRef] = {
    val resultCellList = new mutable.ArrayBuffer[CellRef]
    val col = targetFormulaCellRef.col
    for (targetCell <- targetCells) {
      if ((targetCell.col == col) && targetCell.row < targetFormulaCellRef.row && !cellRefsToExclude.contains(targetCell)) resultCellList.addOne(targetCell)
    }
    resultCellList
  }

}

trait FormulaProcessor {
  /** Processes area formulas
   */
  def processAreaFormulas(transformer: Transformer, area: Area): Unit

  /** 找出公式对应的单元所产生的单元集合
   *  例如某单元格中的公式如：$[D3*0.5+F3]，则需要找出D3和F3所产生新的单元格。
   */
  protected def buildTargetCellRefMap(transformer: Transformer, area: Area, formulaCellData: CellData): collection.Map[CellRef, collection.Seq[CellRef]] = {
    val targetCellRefMap = new mutable.LinkedHashMap[CellRef, mutable.ArrayBuffer[CellRef]]
    val formulaCellRefs = getFormulaCellRefs(formulaCellData.formula)
    for (cellRef <- formulaCellRefs) {
      val pos = CellRef(cellRef)
      if (pos.isValid) {
        if (pos.sheetName == null) pos.sheetName = formulaCellData.sheetName
        val targetCellDataList = transformer.getCellData(pos).orNull.targetPos
        if (targetCellDataList.isEmpty && area != null && !area.getAreaRef.contains(pos)) targetCellDataList.addOne(pos)
        targetCellRefMap.put(pos, targetCellDataList)
      }
    }
    targetCellRefMap
  }

  /** 找出公式对应的连续单元所产生的单元集合
   *  例如某单元格中的公式如：$[SUM(U_(F8,F13))]，则需要找出F8和F13所产生新的单元格。
   */
  protected def buildJointedCellRefMap(transformer: Transformer, formulaCellData: CellData): collection.Map[String, collection.Seq[CellRef]] = {
    val jointedCellRefMap = new mutable.LinkedHashMap[String, collection.Seq[CellRef]]
    val jointedCellRefs = getJointedCellRefs(formulaCellData.formula)
    // for each jointed cell ref build a list of cells into which individual cell names were transformed to
    for (jointedCellRef <- jointedCellRefs) {
      val nestedCellRefs = getCellRefsFromJointedCellRef(jointedCellRef)
      val jointedCellRefList = new mutable.ArrayBuffer[CellRef]
      for (cellRef <- nestedCellRefs) {
        val pos = CellRef(cellRef)
        if (pos.sheetName == null) pos.sheetName = formulaCellData.sheetName
        val targetCellDataList = transformer.getCellData(pos).orNull.targetPos
        jointedCellRefList.addAll(targetCellDataList)
      }
      jointedCellRefMap.put(jointedCellRef, jointedCellRefList)
    }
    jointedCellRefMap
  }

  /** Combines a list of cell references into a range
   * E.g. for cell references A1, A2, A3, A4 it returns A1:A4
   */
  protected def createTargetCellRef(targetCellDataList: collection.Seq[CellRef]): String = {
    if (targetCellDataList == null) return ""
    val size = targetCellDataList.size
    if (size == 0) return ""
    else if (size == 1) return targetCellDataList(0).getCellName()
    // falsify if same sheet
    for (i <- 0 until size - 1) {
      if (!targetCellDataList(i).sheetName.equals(targetCellDataList(i + 1).sheetName)) return buildCellRefsString(targetCellDataList)
    }
    // falsify if rectangular
    val upperLeft = targetCellDataList(0)
    val lowerRight = targetCellDataList(size - 1)
    val rowCount = lowerRight.row - upperLeft.row + 1
    val colCount = lowerRight.col - upperLeft.col + 1
    if (size != colCount * rowCount) return buildCellRefsString(targetCellDataList)
    // Fast exit if horizontal or vertical
    if (rowCount == 1 || colCount == 1) return upperLeft.getCellName() + ":" + lowerRight.getCellName()
    // Hole in rectangle with same cell count check
    // Check if upperLeft is most upper cell and most left cell. And check if lowerRight is most lower cell and most right cell.
    var minRow = upperLeft.row
    var minCol = upperLeft.col
    var maxRow = minRow
    var maxCol = minCol
    for (cell <- targetCellDataList) {
      if (cell.col < minCol) minCol = cell.col
      if (cell.col > maxCol) maxCol = cell.col
      if (cell.row < minRow) minRow = cell.row
      if (cell.row > maxRow) maxRow = cell.row
    }
    if (!(maxRow == lowerRight.row && minRow == upperLeft.row && maxCol == lowerRight.col && minCol == upperLeft.col)) return buildCellRefsString(targetCellDataList)
    // Selection is either vertical, horizontal line or rectangular -> same return structure in each case
    upperLeft.getCellName() + ":" + lowerRight.getCellName()
  }

  private def buildCellRefsString(cellRefs: collection.Seq[CellRef]): String = {
    var reply: String = ""
    for (cellRef <- cellRefs) {
      reply += "," + cellRef.getCellName()
    }
    reply.substring(1)
  }

}
