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
import org.beangle.doc.excel.template.directive.EachDirective
import org.beangle.doc.excel.{CellRef, Sheets}

import java.io.{IOException, InputStream, OutputStream}
import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.Using

/**
 * Excel 模板渲染入口：读取带 jx:* 批注指令的模板，按数据展开并输出 xlsx。
 *
 * 调用方负责关闭输出流；传入的模板输入流在本类 transform 调用后会被关闭。
 *
 * @see org.beangle.doc.excel.template.directive
 */
class TransformHelper(templateStream: InputStream) {
  var deleteTemplateSheet = true
  var processFormulas = true

  @throws[IOException]
  def transform(os: OutputStream, datas: collection.Map[String, Any]): Unit = {
    val transformer = Using.resource(templateStream)(DefaultTransformer.createTransformer)
    val areaBuilder = new XlsCommentAreaBuilder(transformer)
    val context = new Context(datas)
    val areas = areaBuilder.build()
    transformer.removeAllRowBreaks()
    for (area <- areas) {
      area.applyAt(CellRef(area.startCellRef.getCellName(false)), context)
    }
    if (processFormulas) {
      for (area <- areas) {
        area.formulaProcessor = new DefaultFormulaProcessor
        area.processFormulas()
      }
    }

    if (deleteTemplateSheet) {
      getSheetsNameOfMultiSheetTemplate(areas) foreach {
        Sheets.remove(transformer.workbook, _)
      }
    }
    transformer.workbook.setForceFormulaRecalculation(true)
    transformer.write(os)
  }

  /**
   * Return names of all multi sheet template
   */
  private def getSheetsNameOfMultiSheetTemplate(areas: Iterable[Area]): Iterable[String] = {
    val names = new mutable.ArrayBuffer[String]
    for (xlsArea <- areas) {
      var found = false
      for (directive <- xlsArea.findDirectiveByName("each"); if !found) {
        if (Strings.isNotBlank(directive.asInstanceOf[EachDirective].multisheet)) {
          names.addOne(xlsArea.getAreaRef.sheetName)
          found = true
        }
      }
    }
    names
  }
}
