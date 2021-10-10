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

import org.apache.poi.ss.usermodel.DataValidationConstraint.ValidationType.{DECIMAL, INTEGER, TEXT_LENGTH}
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.*
import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.Strings
import org.beangle.doc.excel.schema.{Constraints, ExcelColumn, ExcelSchema}

import java.io.OutputStream
import scala.collection.mutable

object ExcelSchemaWriter {

  def generate(schema: ExcelSchema, os: OutputStream): Unit = {
    val workbook = new XSSFWorkbook()
    for (esheet <- schema.sheets) {
      val sheet = workbook.createSheet(esheet.name)
      sheet.setDefaultColumnWidth(15)
      var rowIdx = 0

      // write title
      esheet.title foreach { title =>
        val cell = writeRow(sheet, title, rowIdx, esheet.columns.size)
        cell.getRow.setHeightInPoints(15)
        cell.setCellStyle(getTitleStyle(workbook))
        rowIdx += 1
      }
      // write remark
      esheet.remark foreach { remark =>
        val cell = writeRow(sheet, remark, rowIdx, esheet.columns.size)
        cell.setCellStyle(getRemarkStyle(workbook))
        rowIdx += 1
      }
      // write column remarks
      val existsColumnRemark = esheet.columns.exists(_.remark.nonEmpty)
      if (existsColumnRemark) {
        val remarkRow = sheet.createRow(rowIdx)
        val remarkStyle = getColumnRemarkStyle(workbook)
        rowIdx += 1
        esheet.columns.indices foreach { i =>
          val col = esheet.columns(i)
          val cell = writeColumnRemark(sheet, col.remark.getOrElse(""), remarkRow, i)
          cell.setCellStyle(remarkStyle)
        }
      }

      // write column(name,comment)
      val defaultStyles = Collections.newMap[String, CellStyle]
      val columnRow = sheet.createRow(rowIdx)
      val optionalStyle = getColumnTitleStyle(workbook, required = false)
      val requiredStyle = getColumnTitleStyle(workbook, required = true)

      val drawing = sheet.createDrawingPatriarch
      val dvHelper = new XSSFDataValidationHelper(sheet)
      esheet.columns.indices foreach { curColumnIdx =>
        val col = esheet.columns(curColumnIdx)
        val cell = writeColumn(sheet, col.name, columnRow, curColumnIdx, col.required)
        col.comment foreach { c =>
          val comment = drawing.createCellComment(new XSSFClientAnchor(0, 0, 0, 0, curColumnIdx, rowIdx, curColumnIdx, rowIdx))
          comment.setString(new XSSFRichTextString(c + ":" + col.dataType))
          cell.setCellComment(comment)
          comment.setVisible(false)
        }
        if (col.required) {
          cell.setCellStyle(requiredStyle)
        } else {
          cell.setCellStyle(optionalStyle)
        }

        if (null == col.datas) {
          if (col.isInt) {
            sheet.addValidationData(Constraints.asNumeric(dvHelper, col, INTEGER, rowIdx + 1, curColumnIdx))
            setDefaultStyle(sheet, defaultStyles, curColumnIdx, col.format.getOrElse("0"))
          } else if (col.isDecimal) {
            sheet.addValidationData(Constraints.asNumeric(dvHelper, col, DECIMAL, rowIdx + 1, curColumnIdx))
            setDefaultStyle(sheet, defaultStyles, curColumnIdx, col.format.getOrElse("General"))
          } else if (col.isDate) {
            sheet.addValidationData(Constraints.asDate(dvHelper, col, rowIdx + 1, curColumnIdx))
            setDefaultStyle(sheet, defaultStyles, curColumnIdx, col.format.get)
          } else if (col.length.nonEmpty) {
            if (col.formular1 == "0" && col.required) {
              col.formular1 = "1"
            }
            if (col.unique) {
              sheet.addValidationData(Constraints.asUnique(dvHelper, col, rowIdx + 1, curColumnIdx))
            } else {
              sheet.addValidationData(Constraints.asNumeric(dvHelper, col, TEXT_LENGTH, rowIdx + 1, curColumnIdx))
            }
            setDefaultStyle(sheet, defaultStyles, curColumnIdx, "@")
          } else if (col.isBool) {
            sheet.addValidationData(Constraints.asBoolean(dvHelper, col, rowIdx + 1, curColumnIdx))
          } else if (null != col.refs && col.refs.nonEmpty) {
            addRefValidation(schema, sheet, dvHelper, col, rowIdx + 1, curColumnIdx)
            setDefaultStyle(sheet, defaultStyles, curColumnIdx, "@")
          }
        } else {
          var dIdx = 1
          col.datas foreach { data =>
            var dataRow = sheet.getRow(dIdx + rowIdx)
            if (null == dataRow) {
              dataRow = sheet.createRow(dIdx + rowIdx)
            }
            dIdx += 1
            dataRow.createCell(curColumnIdx).setCellValue(data)
          }
        }
      }
    }

    workbook.write(os)
    os.close()
  }

  private def setDefaultStyle(sheet: Sheet, defaults: mutable.Map[String, CellStyle], columnIdx: Int, format: String): Unit = {
    val style = defaults.getOrElseUpdate(format, {
      val s = sheet.getWorkbook.createCellStyle()
      val df = sheet.getWorkbook.createDataFormat()
      s.setDataFormat(df.getFormat(format))
      s
    })
    sheet.setDefaultColumnStyle(columnIdx, style)
  }

  private def addRefValidation(schema: ExcelSchema, sheet: Sheet, helper: XSSFDataValidationHelper,
                               col: ExcelColumn, startRowIdx: Int, columnIdx: Int): Boolean = {
    var finded = false
    schema.sheets.find(_.name != sheet.getSheetName) foreach { codeSheet =>
      var codeColIdx = 'A'.toInt
      for (c <- codeSheet.columns if !finded) {
        if (c.datas == col.refs) {
          finded = true
        }
        codeColIdx += 1
      }
      if (finded) {
        val refColumn = (codeColIdx - 1).asInstanceOf[Char]
        val formular = codeSheet.name + "!$" + refColumn + "$2:$" + refColumn + "$" + (col.refs.size + 1) //考虑有个标题，所以+1
        val validation = Constraints.asFormular(helper, formular, col, startRowIdx, columnIdx, "请选择合适的" + col.name)
        sheet.addValidationData(validation)
      }
    }
    finded
  }

  private def writeColumnRemark(sheet: Sheet, content: String, row: Row, columnIdx: Int): Cell = {
    val cell = row.createCell(columnIdx)
    val newLines = Strings.count(content.trim(), "\n")
    if (newLines > 0) {
      val newHeight = (newLines + 1) * sheet.getDefaultRowHeightInPoints
      if (newHeight > row.getHeightInPoints) {
        row.setHeightInPoints(newHeight)
      }
    }
    cell.setCellValue(content)
    cell
  }

  private def writeColumn(sheet: Sheet, content: String, row: Row, columnIdx: Int, required: Boolean): Cell = {
    val cell = row.createCell(columnIdx)
    if (required) {
      cell.setCellValue("*" + content)
    } else {
      cell.setCellValue(content)
    }
    cell
  }

  private def writeRow(sheet: Sheet, content: String, rowIdx: Int, colSpan: Int): Cell = {
    val mergedRegion = new CellRangeAddress(rowIdx, rowIdx, 0, colSpan - 1)
    sheet.addMergedRegion(mergedRegion)

    val row = sheet.createRow(rowIdx)
    val cell = row.createCell(0)

    val newLines = Strings.count(content.trim(), "\n")
    if (newLines > 0) {
      row.setHeightInPoints((newLines + 1) * sheet.getDefaultRowHeightInPoints)
    }
    cell.setCellValue(content)
    cell
  }

  private def getRemarkStyle(wb: Workbook): CellStyle = {
    val style = wb.createCellStyle
    style.setAlignment(HorizontalAlignment.LEFT)
    style.setVerticalAlignment(VerticalAlignment.CENTER)
    style.setWrapText(true)
    style
  }

  private def getTitleStyle(wb: Workbook): CellStyle = {
    val style = wb.createCellStyle
    style.setAlignment(HorizontalAlignment.CENTER)
    style.setVerticalAlignment(VerticalAlignment.CENTER)
    style.setWrapText(true)
    val font = wb.createFont
    font.setFontHeightInPoints(15.toShort)
    font.setFontName("宋体")
    font.setItalic(false)
    font.setBold(true)
    style.setFont(font)
    style
  }

  private def getColumnRemarkStyle(wb: Workbook): CellStyle = {
    val style = wb.createCellStyle.asInstanceOf[XSSFCellStyle]
    style.setAlignment(HorizontalAlignment.CENTER)
    style.setVerticalAlignment(VerticalAlignment.CENTER)
    style.setWrapText(true)
    style.setFillForegroundColor(IndexedColors.AUTOMATIC.index)
    style
  }

  private def getColumnTitleStyle(wb: Workbook, required: Boolean): CellStyle = {
    val style = wb.createCellStyle.asInstanceOf[XSSFCellStyle]
    style.setAlignment(HorizontalAlignment.CENTER) // 左右居中
    style.setVerticalAlignment(VerticalAlignment.CENTER) // 上下居中

    style.setFillPattern(FillPatternType.SOLID_FOREGROUND)
    val rgb = Array(221.toByte, 217.toByte, 196.toByte)
    style.setFillForegroundColor(new XSSFColor(rgb, new DefaultIndexedColorMap))

    style.setBorderTop(BorderStyle.THIN)
    style.setBorderBottom(BorderStyle.THIN)
    style.setBorderLeft(BorderStyle.THIN)
    style.setBorderRight(BorderStyle.THIN)

    val font = wb.createFont.asInstanceOf[XSSFFont]
    font.setBold(true)
    if (required) {
      font.setColor(IndexedColors.RED.index)
    }
    style.setFont(font)
    style
  }

}
