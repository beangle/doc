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

import org.beangle.commons.lang.Strings
import org.beangle.commons.script.ExpressionEvaluator
import org.beangle.doc.excel.template.directive.{AreaDirective, Directive, EachDirective}
import org.beangle.doc.excel.{CellRef, Sheets, Workbooks}

import java.io.{IOException, InputStream, OutputStream}
import scala.collection.mutable

class TransformHelper(templateStream: InputStream) {
  var deleteTemplateSheet = true
  var processFormulas = true

  @throws[IOException]
  def transform(os: OutputStream, datas: collection.Map[String,Any]): Unit = {
    val transformer = DefaultTransformer.createTransformer(templateStream)
    val areaBuilder = new XlsCommentAreaBuilder(transformer)
    val context = new Context(datas)
    val xlsAreaList = areaBuilder.build()
    for (xlsArea <- xlsAreaList) {
      xlsArea.applyAt(CellRef(xlsArea.startCellRef.getCellName(false)), context)
    }
    if (processFormulas) {
      for (xlsArea <- xlsAreaList) {
        xlsArea.formulaProcessor = new DefaultFormulaProcessor
        xlsArea.processFormulas()
      }
    }

    if (deleteTemplateSheet) {
      getSheetsNameOfMultiSheetTemplate(xlsAreaList) foreach {
        Sheets.remove(transformer.workbook, _)
      }
    }
    transformer.write(os)
  }

  /**
   * Return names of all multi sheet template
   */
  private def getSheetsNameOfMultiSheetTemplate(areas: Iterable[Area]): Iterable[String] = {
    val names = new mutable.ArrayBuffer[String]
    for (xlsArea <- areas) {
      var finded = false
      for (directive <- xlsArea.findDirectiveByName("each"); if !finded) {
        if (Strings.isNotBlank(directive.asInstanceOf[EachDirective].multisheet)) {
          names.addOne(xlsArea.getAreaRef.sheetName)
          finded = true
        }
      }
    }
    names
  }
}
