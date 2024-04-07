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

import org.beangle.doc.excel.{AreaRef, CellRef}
import org.slf4j.{Logger, LoggerFactory}

import java.util.*
import java.util.regex.{Matcher, Pattern}
import scala.collection.mutable
import org.beangle.doc.excel.template.FormulaProcessor.*

class DefaultFormulaProcessor extends FormulaProcessor {
  private val logger = LoggerFactory.getLogger(classOf[DefaultFormulaProcessor])

  /**
   * The method transforms all the formula cells according to the area
   * transformations happened during the area processing
   */
  override def processAreaFormulas(transformer: Transformer, area: Area): Unit = {
    val formulaCells = transformer.getFormulaCells
    for (fcd <- formulaCells; if !(fcd.area == null || !area.getAreaRef.sheetName.equals(fcd.sheetName))) {
      logger.debug("Processing formula cell {}", fcd)
      val targetFormulaCells = fcd.targetPos
      val targetCellRefMap = buildTargetCellRefMap(transformer, area, fcd)
      val jointedCellRefMap = buildJointedCellRefMap(transformer, fcd)
      val usedCellRefs = new mutable.HashSet[CellRef]
      // process all of the result (target) formula cells
      // a result formula cell is a cell into which the original cell with the formula was transformed
      for (i <- targetFormulaCells.indices) {
        val targetFormulaCellRef = targetFormulaCells(i)
        var targetFormulaString = fcd.formula
        if (fcd.isParameterizedFormulaCell && i < fcd.evaluatedFormulas.size) targetFormulaString = fcd.evaluatedFormulas(i)
        val formulaSourceAreaRef = fcd.area.getAreaRef
        val formulaTargetAreaRef = fcd.targetParentAreaRef(i)
        var isFormulaCellRefsEmpty = true
        for (cellRefEntry <- targetCellRefMap) {
          val targetCells = cellRefEntry._2
          if (targetCells.nonEmpty) {
            isFormulaCellRefsEmpty = false
            var replacementCells = findFormulaCellRefReplacements(transformer, targetFormulaCellRef, formulaSourceAreaRef, formulaTargetAreaRef, cellRefEntry)
            if (fcd.formulaStrategy == CellData.FormulaStrategy.BY_COLUMN) { // for BY_COLUMN formula strategy we take only a subset of the cells
              replacementCells = createTargetCellRefListByColumn(targetFormulaCellRef, replacementCells, usedCellRefs)
              usedCellRefs.addAll(replacementCells)
            }
            val replacementString = createTargetCellRef(replacementCells)
            val from = regexJointedLookBehind + regexExcludePrefixSymbols + Pattern.quote(cellRefEntry._1.getCellName(true))
            val to = Matcher.quoteReplacement(replacementString)
            targetFormulaString = targetFormulaString.replaceAll(from, to)
          }
        }
        var isFormulaJointedCellRefsEmpty = true
        // iterate through all the jointed cell references used in the formula
        for (jointedCellRefEntry <- jointedCellRefMap) {
          val targetCellRefList = jointedCellRefEntry._2
          if (targetCellRefList.nonEmpty) {
            isFormulaJointedCellRefsEmpty = false
            val cellRefMapEntryParam = (null, targetCellRefList.sorted)
            val replacementCells = findFormulaCellRefReplacements(transformer, targetFormulaCellRef, formulaSourceAreaRef, formulaTargetAreaRef, cellRefMapEntryParam)
            val replacementString = createTargetCellRef(replacementCells)
            targetFormulaString = targetFormulaString.replaceAll(Pattern.quote(jointedCellRefEntry._1), replacementString)
          }
        }
        val sheetNameReplacementRegex = Pattern.quote(targetFormulaCellRef.getFormattedSheetName + CellRef.SHEET_NAME_DELIMITER)
        targetFormulaString = targetFormulaString.replaceAll(sheetNameReplacementRegex, "")
        // if there were no regular or jointed cell references found for this formula use a default value
        // if set or 0
        if (isFormulaCellRefsEmpty && isFormulaJointedCellRefsEmpty && (!fcd.isParameterizedFormulaCell || fcd.isJointedFormulaCell)) {
          targetFormulaString = if (fcd.defaultValue != null) fcd.defaultValue else "0"
        }
        if (targetFormulaString.nonEmpty) transformer.setFormula(new CellRef(targetFormulaCellRef.sheetName, targetFormulaCellRef.row, targetFormulaCellRef.col), targetFormulaString)
      }
    }
  }

  private def findFormulaCellRefReplacements(transformer: Transformer, targetFormulaCellRef: CellRef,
                                             formulaSourceAreaRef: AreaRef, formulaTargetAreaRef: AreaRef,
                                             entry: (CellRef, collection.Seq[CellRef])): collection.Seq[CellRef] = {
    val cellReference = entry._1
    val targets = entry._2
    if (cellReference != null && !formulaSourceAreaRef.contains(cellReference)) {
      val cd = transformer.getCellData(cellReference).orNull
      if (cd != null && cd.targetParentAreaRef.nonEmpty) {
        val targetReferences = new mutable.ArrayBuffer[CellRef]
        cd.targetParentAreaRef.find(_.contains(targetFormulaCellRef)) match {
          case Some(ar) =>
            targets.foreach(x => if (ar.contains(x)) targetReferences.addOne(x))
            return targetReferences
          case None =>
        }
      }
      targets
    } else {
      targets.filter(formulaTargetAreaRef.contains)
    }
  }

}
