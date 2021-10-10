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

import org.beangle.commons.bean.Properties
import org.beangle.commons.lang.Strings
import org.beangle.doc.excel.template.Notation.*
import org.beangle.doc.excel.template.XlsCommentAreaBuilder.*
import org.beangle.doc.excel.template.directive.*
import org.beangle.doc.excel.{AreaRef, CellRef}
import org.slf4j.{Logger, LoggerFactory}

import java.util.concurrent.ConcurrentHashMap
import java.util.regex.{Matcher, Pattern}
import scala.collection.mutable

class XlsCommentAreaBuilder(val transformer: Transformer, val clearTemplateCells: Boolean = true) {
  private val logger = LoggerFactory.getLogger(classOf[XlsCommentAreaBuilder])

  /**
   * Builds a list of area objects defined by top level area markup ("jx:area")
   * containing a tree of all nested directives
   */
  def build(): collection.Seq[Area] = {
    val userAreas = new mutable.ArrayBuffer[Area]
    val commentedCells = transformer.getCommentedCells
    val allDirectives = new mutable.ArrayBuffer[DirectiveData]
    val allAreas = new mutable.ArrayBuffer[Area]
    commentedCells foreach { cellData =>
      val comment = cellData.cellComment
      val directiveDatas = buildDirectiveDatas(cellData, comment)
      directiveDatas foreach { data =>
        data.directive match {
          case _: AreaDirective =>
            val userArea = Area(data.areaRef, transformer)
            allAreas.addOne(userArea)
            userAreas.addOne(userArea)
          case _ =>
            allAreas.addAll(data.directive.areas)
            allDirectives.addOne(data)
        }
      }
    }
    for (i <- allDirectives.indices) {
      val data = allDirectives(i)
      val cmdAreaRef = data.areaRef
      val cmdAreas = data.directive.areas
      var minArea: Area = null
      val minAreas = new mutable.ArrayBuffer[Area]
      for (area <- allAreas) {
        if (!cmdAreas.contains(area) && area.getAreaRef.contains(cmdAreaRef)) {
          var belongsToNextCmd = false
          for (j <- i + 1 until allDirectives.size; if !belongsToNextCmd) {
            val nextCmd = allDirectives(j)
            if (nextCmd.directive.areas.contains(area)) {
              belongsToNextCmd = true
            }
          }
          if (!(belongsToNextCmd || (minArea != null && !minArea.getAreaRef.contains(area.getAreaRef)))) {
            if (minArea != null && minArea == area) minAreas.addOne(area)
            else {
              minArea = area
              minAreas.clear()
              minAreas.addOne(minArea)
            }
          }
        }
      }
      minAreas foreach (_.addDirective(data.areaRef, data.directive))
    }
    if (clearTemplateCells) userAreas foreach (_.clearCells())
    userAreas
  }

  private def buildDirectiveDatas(cellData: CellData, text: String): Iterable[DirectiveData] = {
    val directiveDatas = new mutable.ArrayBuffer[DirectiveData]
    val commentLines = mutable.ArraySeq.make(text.split("\\n"))

    for (commentLine <- commentLines) {
      val line = commentLine.trim
      if (isDirectiveString(line)) {
        val nameEndIndex = line.indexOf(ATTR_PREFIX, DirectivePrefox.length)
        if (nameEndIndex < 0) {
          val errMsg = "Failed to parse directive line [" + line + "]. Expected '" + ATTR_PREFIX + "' symbol."
          throw new IllegalStateException(errMsg)
        }
        val directiveName = line.substring(DirectivePrefox.length, nameEndIndex).trim
        val attrMap = buildAttrMap(line, nameEndIndex)
        val lastCellRef = attrMap.get(LAST_CELL_ATTR_NAME).orNull
        if (lastCellRef == null) {
          logger.warn("Failed to find last cell ref attribute '" + LAST_CELL_ATTR_NAME + "' for directive '" + directiveName + "' in cell " + cellData.cellRef)
        } else {
          val lastCell = CellRef(lastCellRef)
          if (Strings.isBlank(lastCell.sheetName)) lastCell.sheetName = cellData.sheetName
          var cmdAreas = buildAreas(cellData, line)
          val locationAreaRef = new AreaRef(cellData.cellRef, lastCell)
          if (cmdAreas.isEmpty) {
            cmdAreas = List(Area(locationAreaRef, transformer))
          }
          DirectiveFactory.newDirective(directiveName, attrMap, cmdAreas).foreach { d =>
            directiveDatas.addOne(DirectiveData(locationAreaRef, d))
          }
        }
      }
    }
    directiveDatas
  }

  private def buildAreas(cellData: CellData, cmdLine: String): Iterable[Area] = {
    val areas = new mutable.ArrayBuffer[Area]
    val areasAttrMatcher = AREAS_ATTR_REGEX_PATTERN.matcher(cmdLine)
    if (areasAttrMatcher.find) {
      val areasAttr = areasAttrMatcher.group
      val areaRefs = extractAreaRefs(cellData, areasAttr)
      areas ++= areaRefs.map(Area(_, transformer))
    }
    areas
  }

  private def extractAreaRefs(cellData: CellData, areasAttr: String): Iterable[AreaRef] = {
    val areaRefs = new mutable.ArrayBuffer[AreaRef]
    val areaRefMatcher = FormulaProcessor.regexAreaRefPattern.matcher(areasAttr)
    while (areaRefMatcher.find) {
      val areaRefName = areaRefMatcher.group
      val areaRef = AreaRef(areaRefName)
      if (Strings.isBlank(areaRef.sheetName)) areaRef.firstCellRef.sheetName = cellData.sheetName
      areaRefs.addOne(areaRef)
    }
    areaRefs
  }

  private def buildAttrMap(cmdLine: String, nameEndIndex: Int): collection.Map[String, String] = {
    val paramsEndIndex = cmdLine.lastIndexOf(ATTR_SUFFIX)
    if (paramsEndIndex < 0) {
      val errMsg = "Failed to parse directive line [" + cmdLine + "]. Expected '" + ATTR_SUFFIX + "' symbol."
      logger.error(errMsg)
      throw new IllegalArgumentException(errMsg)
    }
    val attrString = cmdLine.substring(nameEndIndex + 1, paramsEndIndex).trim
    CellData.parseDirectiveAttributes(attrString)
  }

}
