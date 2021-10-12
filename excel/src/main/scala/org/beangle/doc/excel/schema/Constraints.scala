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

package org.beangle.doc.excel.schema

import org.apache.poi.ss.usermodel.DataValidationConstraint.OperatorType.{BETWEEN, GREATER_OR_EQUAL}
import org.apache.poi.ss.usermodel.DataValidationConstraint.ValidationType.{DECIMAL, INTEGER, TEXT_LENGTH}
import org.apache.poi.ss.usermodel.{DataValidation, DataValidationConstraint, DateUtil}
import org.apache.poi.ss.util.CellRangeAddressList
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper

import java.text.SimpleDateFormat

/** 构建Excel约束
 * 生成的约束需要添加到工作表中
 *
 * @see org.apache.poi.xssf.usermodel.XSSFSheet.addValidationData
 */
object Constraints {

  /** 构建Bool约束
   *
   * @param helper
   * @param col
   * @param startRowIdx
   * @param columnIdx
   * @return
   */
  def asBoolean(helper: XSSFDataValidationHelper, col: ExcelColumn, startRowIdx: Int, columnIdx: Int): DataValidation = {
    val constraint = helper.createExplicitListConstraint(Array("Y", "N"))
    val v = createValidation(helper, col, startRowIdx, columnIdx, constraint, "请选择Y/N")
    v.setSuppressDropDownArrow(true)
    v
  }

  def asFormular(helper: XSSFDataValidationHelper, formular: String, col: ExcelColumn, startRowIdx: Int,
                 columnIdx: Int, prompt: String): DataValidation = {
    val constraint = helper.createFormulaListConstraint(formular)
    val validation = createValidation(helper, col, startRowIdx, columnIdx, constraint, "请选择合适的" + col.name)
    validation.setSuppressDropDownArrow(true)
    validation
  }

  def asDate(helper: XSSFDataValidationHelper, col: ExcelColumn, startRowIdx: Int, columnIdx: Int): DataValidation = {
    var prompt: String = null
    var constraint: DataValidationConstraint = null
    val format = col.format.get
    val sdf = new SimpleDateFormat(format)
    val formual1Value = DateUtil.getExcelDate(sdf.parse(col.formular1)).toString
    val lf = format.toLowerCase()
    val cellContent = if (lf.contains("yyyy")) {
      if (lf.contains("hh")) "日期时间" else "日期"
    } else {
      "时间"
    }
    col.formular2 match {
      case None =>
        constraint = helper.createDateConstraint(GREATER_OR_EQUAL, formual1Value, null, format)
        prompt = composeError(cellContent, col.formular1, None)
      case Some(f2) =>
        val formual2Value = DateUtil.getExcelDate(sdf.parse(f2)).toString
        constraint = helper.createDateConstraint(BETWEEN, formual1Value, formual2Value, format)
        prompt = composeError(cellContent, col.formular1, col.formular2)
    }
    createValidation(helper, col, startRowIdx, columnIdx, constraint, prompt)
  }

  def asNumeric(helper: XSSFDataValidationHelper, col: ExcelColumn, validationType: Int, startRowIdx: Int, columnIdx: Int): DataValidation = {
    var prompt: String = null
    var constraint: DataValidationConstraint = null
    var unit = "值"
    val dataType = validationType match {
      case INTEGER => "整数"
      case DECIMAL => "小数"
      case TEXT_LENGTH =>
        unit = "长度"; "文本"
      case _ => ""
    }

    col.formular2 match {
      case None =>
        constraint = helper.createNumericConstraint(validationType, GREATER_OR_EQUAL, col.formular1, null)
        prompt = composeError(dataType, col.formular1, None)

      case Some(f2) =>
        constraint = helper.createNumericConstraint(validationType, BETWEEN, col.formular1, f2)
        prompt = composeError(dataType, col.formular1, col.formular2, unit)
    }
    createValidation(helper, col, startRowIdx, columnIdx, constraint, prompt)
  }

  def asUnique(helper: XSSFDataValidationHelper, col: ExcelColumn, startRowIdx: Int, columnIdx: Int): DataValidation = {
    val cn = ('A'.toInt + columnIdx).asInstanceOf[Char] //column name
    val rn = startRowIdx + 1 //row name
    //=COUNTIF($D$5:D5,D5)=1
    var formular = "COUNTIF($" + cn + "$" + rn + ":" + cn + rn + "," + cn + rn + ")=1"
    var error = "该列不允许有重复"
    if (col.length.nonEmpty) {
      val cname = cn.toString + rn.toString //cell name
      formular = "AND(AND(LEN(" + cname + ") >= " + col.formular1 + ",LEN(" + cname + ") <= " + col.length.get + ")," + formular + ")"
      error += ",并且" + composeError("文本", col.formular1, col.formular2, "长度")
    }

    val constraint = helper.createCustomConstraint("=" + formular)
    val addressList = new CellRangeAddressList(startRowIdx, 1048576 - 1, columnIdx, columnIdx)
    val validation = helper.createValidation(constraint, addressList)
    validation.createErrorBox(col.name + "输入错误", error)
    validation.setShowErrorBox(true)
    validation.setShowPromptBox(true)
    validation.setSuppressDropDownArrow(true)
    validation
  }

  private def createValidation(helper: XSSFDataValidationHelper, col: ExcelColumn, startRowIdx: Int,
                               columnIdx: Int, constraint: DataValidationConstraint, prompt: String): DataValidation = {
    //1048576 is max row in xlsx
    val addressList = new CellRangeAddressList(startRowIdx, 1048576 - 1, columnIdx, columnIdx)
    val validation = helper.createValidation(constraint, addressList)
    validation.createErrorBox(col.name + "输入有误", prompt)
    validation.setShowErrorBox(true)
    validation.setSuppressDropDownArrow(false)
    validation.setEmptyCellAllowed(!col.required)
    validation
  }

  def composeError(what: String, min: String, max: Option[String], quantifier: String = ""): String = {
    max match {
      case None => "请输入" + quantifier + "大于等于" + min + "的" + what
      case Some(m) =>
        if (m == min) {
          "请输入" + quantifier + "为" + min + "的" + what
        } else {
          "请输入" + quantifier + "在" + min + "~" + m + "之间的" + what
        }
    }
  }

}
