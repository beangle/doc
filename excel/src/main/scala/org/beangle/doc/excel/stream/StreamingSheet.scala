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

package org.beangle.doc.excel.stream

import org.apache.poi.ss.usermodel.{BorderStyle, CellStyle, HorizontalAlignment}
import org.apache.poi.ss.util.{CellRangeAddress, RegionUtil}
import org.apache.poi.xssf.streaming.{SXSSFSheet, SXSSFWorkbook}
import org.beangle.doc.excel.{CellOps, ExcelStyleRegistry}


/** 流式 Sheet — 写入数据的入口。
 *
 * @param workbook 底层 SXSSFWorkbook
 * @param sheet    底层 SXSSFSheet
 * @param registry 样式注册表，用于 fillin 自动应用日期/数字等格式
 */
class StreamingSheet(private[stream] val workbook: SXSSFWorkbook,
                     private[stream] val sheet: SXSSFSheet)
                    (implicit val registry: ExcelStyleRegistry = new ExcelStyleRegistry(workbook)) {

  private var currentRowNum = 0

  /** 获取 Sheet 名称 */
  def getSheetName: String = sheet.getSheetName

  /** 获取底层 SXSSFSheet（高级用法：自定义样式等） */
  private[stream] def getSheet: SXSSFSheet = sheet

  /** 获取当前行号 */
  def getCurrentRowNum: Int = currentRowNum

  /** 写入表头行（默认加粗居中样式） */
  def writeHeaders(headers: String*): this.type = {
    writeHeaders(headers, createHeaderStyle())
  }

  /** 写入表头行（指定样式），可选列宽和行高 */
  def writeHeaders(headers: Seq[String], style: CellStyle,
                   widths: Seq[Int] = Nil, rowHeight: Option[Short] = None): this.type = {
    val row = sheet.createRow(currentRowNum)
    headers.zipWithIndex.foreach { (h, i) =>
      val cell = row.createCell(i)
      cell.setCellValue(if (h == null) "" else h)
      cell.setCellStyle(style)
    }
    widths.zipWithIndex.foreach { (w, i) => sheet.setColumnWidth(i, w * 256) }
    rowHeight.foreach(h => row.setHeight(h))
    currentRowNum += 1
    this
  }

  /** 写入标题行（合并单元格，跨 colCount 列） */
  def writeCaption(text: String, colCount: Int, style: CellStyle, addBorder: Boolean = true): this.type = {
    val row = sheet.createRow(currentRowNum)
    val cell = row.createCell(0)
    cell.setCellValue(text)
    cell.setCellStyle(style)
    if (colCount > 1) {
      val region = new CellRangeAddress(currentRowNum, currentRowNum, 0, colCount - 1)
      sheet.addMergedRegion(region)
      if (addBorder) RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet)
    }
    currentRowNum += 1
    this
  }

  /** 写入一行数据，通过 CellOps.fillin 自动识别类型并应用样式 */
  def writeRow(values: Any*): this.type = {
    import CellOps.toCell
    val row = sheet.createRow(currentRowNum)
    values.zipWithIndex.foreach { (v, i) =>
      row.createCell(i).fillin(v)
    }
    currentRowNum += 1
    this
  }

  /** 设置列宽（字符数） */
  def setColumnWidths(widths: Int*): this.type = {
    widths.zipWithIndex.foreach { (w, i) =>
      sheet.setColumnWidth(i, w * 256)
    }
    this
  }

  /** 冻结窗格 */
  def freezePane(colSplit: Int, rowSplit: Int): this.type = {
    sheet.createFreezePane(colSplit, rowSplit)
    this
  }

  /** 手动刷盘（释放内存到磁盘） */
  def flush(): this.type = {
    sheet.flushRows()
    this
  }

  private def createHeaderStyle(): CellStyle = {
    val style = workbook.createCellStyle()
    val font = workbook.createFont()
    font.setBold(true)
    style.setFont(font)
    style.setAlignment(HorizontalAlignment.CENTER)
    style
  }
}
