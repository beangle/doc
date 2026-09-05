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

package org.beangle.doc.excel

import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.{CellRangeAddress, RegionUtil}
import org.apache.poi.xssf.streaming.{SXSSFSheet, SXSSFWorkbook}

import java.io.{File, FileOutputStream, OutputStream}
import scala.util.Using

/** 流式 Excel 写入器 — 基于 POI SXSSFWorkbook。
 *
 * 内存中只保留滑动窗口大小的行数（默认 100），超出部分自动刷到磁盘临时文件。
 * 适合写入数十万至百万行的大文件场景。
 *
 * 与 [[org.beangle.doc.excel.schema.ExcelSchemaWriter]] 的区别：
 * - ExcelSchemaWriter 用 XSSFWorkbook（DOM），支持校验、公式、下拉列表等，适合模板/表单
 * - StreamingExcelWriter 用 SXSSFWorkbook（流式），内存低，适合大数据导出
 *
 * <h3>基本用法</h3>
 * <pre>
 * Using.resource(new StreamingExcelWriter()) { writer =>
 * writer.addSheet("员工信息")
 * writer.writeHeaders("姓名", "年龄", "部门")
 * empList.foreach(emp => writer.writeRow(emp.name, emp.age, emp.dept))
 * writer.save("employees.xlsx")
 * }
 * </pre>
 *
 * <h3>大数据量（百万行）</h3>
 * <pre>
 * Using.resource(new StreamingExcelWriter(windowSize = 500)) { writer =>
 * writer.addSheet("数据")
 * writer.writeHeaders("ID", "名称", "金额")
 * hugeDataSet.foreach(d => writer.writeRow(d.id, d.name, d.amount))
 * writer.save("large.xlsx")
 * }
 * </pre>
 */
class StreamingExcelWriter(val windowSize: Int = 100, val countPerSheet: Int = 100000) extends AutoCloseable {

  private val workbook = new SXSSFWorkbook(windowSize)
  workbook.setCompressTempFiles(true)
  private val registry = new ExcelStyleRegistry(workbook)

  private val sheets = new scala.collection.mutable.ArrayBuffer[StreamingSheet]
  private var _currentSheet: StreamingSheet = _
  //写入数据的行署，不含标题、caption
  private var dataRowCount = 0
  // 构造时确保有一个默认 Sheet
  if (workbook.getNumberOfSheets == 0) addSheet(null)

  /** 创建新 Sheet 并切换为当前活跃 Sheet。name 为 null 或空时由 POI 自动生成名称。 */
  private def addSheet(name: String): Unit = {
    val sxssfSheet =
      if (name == null || name.isBlank) workbook.createSheet()
      else workbook.createSheet(name)
    val sheet = new StreamingSheet(workbook, sxssfSheet)(registry)
    sheets += sheet
    dataRowCount = 0
    _currentSheet = sheet
  }

  /** 重命名当前活跃 Sheet */
  def changeSheetName(name: String): Unit = {
    val idx = workbook.getSheetIndex(_currentSheet.getSheet)
    workbook.setSheetName(idx, name)
  }

  /** 当前活跃 Sheet 的行号，无 Sheet 时返回 0 */
  def getCurrentRowNum: Int = if (_currentSheet == null) 0 else _currentSheet.getCurrentRowNum

  /** 写入表头行（委托当前 Sheet） */
  def writeHeaders(headers: String*): this.type = {
    _currentSheet.writeHeaders(headers *);
    this
  }

  /** 写入表头行（指定样式，委托当前 Sheet） */
  def writeHeaders(headers: Seq[String], style: CellStyle,
                   widths: Seq[Int] = Nil, rowHeight: Option[Short] = None): this.type = {
    _currentSheet.writeHeaders(headers, style, widths, rowHeight);
    this
  }

  /** 写入标题行（委托当前 Sheet） */
  def writeCaption(text: String, colCount: Int, style: CellStyle, addBorder: Boolean = true): this.type = {
    _currentSheet.writeCaption(text, colCount, style, addBorder);
    this
  }

  /** 写入一行数据（委托当前 Sheet） */
  def writeRow(values: Any*): this.type = {
    if (countPerSheet > 0 && dataRowCount >= countPerSheet) {
      addSheet(null)
    }
    _currentSheet.writeRow(values *)
    dataRowCount += 1
    this
  }

  /** 设置列宽（委托当前 Sheet） */
  def setColumnWidths(widths: Int*): this.type = {
    _currentSheet.setColumnWidths(widths *);
    this
  }

  /** 冻结窗格（委托当前 Sheet） */
  def freezePane(colSplit: Int, rowSplit: Int): this.type = {
    _currentSheet.freezePane(colSplit, rowSplit);
    this
  }

  /** 获取底层 Workbook（高级用法） */
  def getWorkbook: SXSSFWorkbook = workbook

  /** 保存到文件 */
  def save(filePath: String): Unit = save(new File(filePath))

  /** 保存到文件 */
  def save(file: File): Unit = {
    val parent = file.getParentFile
    if (parent != null && !parent.exists()) parent.mkdirs()
    Using.resource(new FileOutputStream(file)) { fos =>
      save(fos)
    }
  }

  /** 保存到输出流并关闭 workbook */
  def save(os: OutputStream): Unit = {
    try {
      workbook.write(os)
    } finally {
      workbook.close()
    }
  }

  override def close(): Unit = workbook.close()
}

/** 流式 Sheet — 写入数据的入口。
 *
 * @param workbook 底层 SXSSFWorkbook
 * @param sheet    底层 SXSSFSheet
 * @param registry 样式注册表，用于 fillin 自动应用日期/数字等格式
 */
class StreamingSheet(private[excel] val workbook: SXSSFWorkbook,
                     private[excel] val sheet: SXSSFSheet)
                    (implicit val registry: ExcelStyleRegistry = new ExcelStyleRegistry(workbook)) {

  private var currentRowNum = 0

  /** 获取 Sheet 名称 */
  def getSheetName: String = sheet.getSheetName

  /** 获取底层 SXSSFSheet（高级用法：自定义样式等） */
  private[excel] def getSheet: SXSSFSheet = sheet

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
