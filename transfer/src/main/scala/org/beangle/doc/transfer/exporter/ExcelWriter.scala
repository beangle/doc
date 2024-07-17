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

package org.beangle.doc.transfer.exporter

import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.{CellRangeAddress, RegionUtil}
import org.apache.poi.xssf.streaming.{SXSSFSheet, SXSSFWorkbook}
import org.apache.poi.xssf.usermodel.*
import org.beangle.commons.lang.Chars
import org.beangle.doc.excel.CellOps.*
import org.beangle.doc.excel.ExcelStyleRegistry
import org.beangle.doc.transfer.Format

import java.io.OutputStream

/**
 * ExcelItemWriter class.
 *
 * @author chaostone
 */
class ExcelWriter(val outputStream: OutputStream) extends Writer {

  protected var workbook: SXSSFWorkbook = _ // 建立新XSSFWorkbook对象

  protected var sheet: SXSSFSheet = _

  private implicit var registry: ExcelStyleRegistry = _

  protected var titles: Array[String] = _

  protected var caption: Option[String] = None

  var flushCount = 1000

  var countPerSheet = 100000

  protected var index = 0

  init()

  def init(): Unit = {
    workbook = new SXSSFWorkbook(flushCount)
    registry = new ExcelStyleRegistry(workbook)
  }

  def close(): Unit = {
    try {
      workbook.write(outputStream)
    } finally {}
    workbook.close()
  }

  override def write(obj: Any): Unit = {
    if (index + 1 >= countPerSheet) {
      writeHeader(caption, titles)
    }
    writeItem(obj)
    index += 1
  }

  def createScheet(name: String): SXSSFSheet = {
    if (null == sheet || null != name && !(this.sheet.getSheetName == name)) {
      this.sheet = if null != name then this.workbook.createSheet(name) else this.workbook.createSheet()
    }
    this.sheet
  }

  private def writeCaption(caption: Option[String]): Unit = {
    caption foreach { c =>
      val row = sheet.createRow(index) // 建立新行
      val cell = row.createCell(0)
      cell.fillin(c)
      cell.setCellStyle(buildCaptionStyle())
      val region = new CellRangeAddress(index, index, 0, titles.length - 1)
      sheet.addMergedRegion(region)
      RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet)
      this.index += 1
    }
  }

  override def writeHeader(caption: Option[String], titles: Array[String]): Unit = {
    createScheet(null)
    this.titles = titles
    this.caption = caption
    this.index = 0
    writeCaption(caption)
    writeItem(titles)
    val titleRow = sheet.getRow(index)
    val titleStyle = buildTitleStyle()

    val maxWith = 15 * 2 //max 15 chinese chars
    var h = 0d // number rows
    for (i <- titles.indices) {
      titleRow.getCell(i).setCellStyle(titleStyle)
      val n = Chars.charLength(titles(i))
      val w = Math.min(n, maxWith)
      val r = n * 1.0 / maxWith
      if (r > h) h = r
      sheet.setColumnWidth(i.toShort, (w + 4) * 256) // 4 is margin
    }
    var height = Math.ceil(h).toInt
    if (height > 8) height = 8
    titleRow.setHeight((height * 12 * 20).toShort)

    index += 1
    sheet.createFreezePane(0, index)
  }

  final def format: Format = Format.Xlsx

  protected def writeItem(datas: Any): Unit = {
    val row = sheet.createRow(index) // 建立新行
    datas match
      case null =>
      case a: Array[Any] =>
        a.indices foreach { i => row.createCell(i).fillin(a(i)) }
      case it: Iterable[Any] =>
        var i = 0
        it.foreach { obj => row.createCell(i).fillin(obj); i += 1 }
      case n: Number =>
        val cell = row.createCell(0)
        cell.setCellType(CellType.NUMERIC)
        cell.setCellValue(new XSSFRichTextString(n.toString))
      case a: Any =>
        val cell = row.createCell(0)
        cell.setCellValue(new XSSFRichTextString(a.toString))
  }

  protected def buildTitleStyle(): XSSFCellStyle = {
    val style = workbook.createCellStyle().asInstanceOf[XSSFCellStyle]
    style.setAlignment(HorizontalAlignment.CENTER) // 左右居中
    style.setVerticalAlignment(VerticalAlignment.CENTER) // 上下居中
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND)
    style.setWrapText(true) //auto wrap text
    style.setFillForegroundColor(getHeaderForegroundColor())
    style
  }

  protected def buildCaptionStyle(): XSSFCellStyle = {
    val style = workbook.createCellStyle().asInstanceOf[XSSFCellStyle]
    style.setAlignment(HorizontalAlignment.CENTER) // 左右居中
    style.setVerticalAlignment(VerticalAlignment.CENTER) // 上下居中
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND)
    style.setFillForegroundColor(getHeaderForegroundColor())
    val font = workbook.createFont()
    font.setBold(true)
    style.setFont(font)
    style
  }

  protected def getHeaderForegroundColor(): XSSFColor = {
    val rgb = Array(221.toByte, 217.toByte, 196.toByte)
    new XSSFColor(rgb, new DefaultIndexedColorMap)
  }

}
