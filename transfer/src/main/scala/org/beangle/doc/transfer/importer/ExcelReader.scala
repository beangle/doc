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

package org.beangle.doc.transfer.importer

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.beangle.commons.io.DataType
import org.beangle.commons.lang.Strings
import org.beangle.commons.logging.Logging
import org.beangle.doc.excel.CellOps.*
import org.beangle.doc.transfer.Format
import org.beangle.doc.transfer.importer.{Attribute, Reader}

import java.io.InputStream

/**
  * Excel的每行一条数据的读取器
  *
  * @author chaostone
  */
class ExcelReader(is: InputStream, sheetNum: Int = 0, val format: Format = Format.Xlsx) extends Reader with Logging {

  /** 读取的工作表 */
  private val sheet = buildSheet(is, sheetNum)

  /** 下一个要读取的位置 标题行默认占据0 */
  private var indexInSheet: Int = 1

  /** 读取的属性 */
  private var attrs: List[Attribute] = _

  private def buildSheet(is: InputStream, sheetNum: Int): Sheet = {
    format match {
      case Format.Xls => new HSSFWorkbook(is).getSheetAt(sheetNum)
      case Format.Xlsx => new XSSFWorkbook(is).getSheetAt(sheetNum)
      case _ => throw new RuntimeException("Cannot support excel format " + format)
    }
  }

  override def readAttributes(): List[Attribute] = {
    var i = 0
    var attrs: List[Attribute] = List.empty
    while (i < 10 && attrs.isEmpty) {
      attrs = this.readAttributes(sheet, i)
      i += 1
    }
    this.indexInSheet = i
    this.attrs = attrs
    attrs
  }

  /**
    * 读取注释
    */
  protected def readAttributes(sheet: Sheet, rowIndex: Int): List[Attribute] = {
    val row = sheet.getRow(rowIndex)
    val attrList = new collection.mutable.ListBuffer[Attribute]
    var hasEmptyCell = false
    for (i <- 0 until row.getLastCellNum; if !hasEmptyCell) {
      val cell = row.getCell(i)
      val comment = cell.getCellComment
      if (null == comment || Strings.isEmpty(comment.getString.getString)) {
        hasEmptyCell = true
      } else {
        var commentStr = comment.getString.getString.trim()
        var dataType = DataType.String
        if (commentStr.indexOf(':') > 0) {
          dataType = DataType.valueOf(Strings.substringAfterLast(commentStr, ":"))
          commentStr = Strings.substringBefore(commentStr, ":")
        }
        attrList += Attribute(i + 1, commentStr.trim(), dataType, cell.getRichStringCellValue.getString)
      }
    }
    attrList.toList
  }

  override def read(): Array[Any] = {
    if (indexInSheet > sheet.getLastRowNum) {
      return null
    }
    val row = sheet.getRow(indexInSheet)
    indexInSheet += 1
    // 如果是个空行,返回空记录
    val attrCount = attrs.size
    if (row == null) {
      new Array[Any](attrCount)
    } else {
      val values = new Array[Any](attrCount)
      values.indices foreach { k =>
        values(k) = getCellValue(row.getCell(k), attrs(k))
      }
      values
    }
  }

  /**
    * 取cell单元格中的数据
    */
  def getCellValue(cell: Cell, attribute: Attribute): Any = {
    if (cell == null) return null
    cell.getValue(attribute.dataType)
  }

  override def close(): Unit = {
    this.sheet.getWorkbook.close()
  }

  /**   当前数据的位置   */
  override def location: String = {
    indexInSheet.toString
  }
}
