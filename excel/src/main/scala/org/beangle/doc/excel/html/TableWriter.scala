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

package org.beangle.doc.excel.html

import org.apache.poi.ss.usermodel.{Cell, HorizontalAlignment, Sheet, VerticalAlignment}
import org.apache.poi.ss.util.{CellRangeAddress, RegionUtil}
import org.apache.poi.xssf.usermodel.*
import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.Strings
import org.beangle.doc.html.*

/** 将HTML中的表格，转换到Excel中
 */
object TableWriter {
  def writer(html: String): XSSFWorkbook = {
    val workbook = new XSSFWorkbook()
    val body = TableParser.parse(html).body

    val tables = body.childNodes.filter(_.name == "table")
    tables foreach { table =>
      val sheetName = table.attributes.get("data-sheet-name").orNull
      var sheet: XSSFSheet = null
      if (null == sheetName) {
        sheet = workbook.createSheet()
      } else {
        sheet = workbook.createSheet(sheetName)
      }
      TableWriter.write(table.asInstanceOf[Table], sheet, 0)
    }
    workbook
  }

  def write(table: Table, sheet: XSSFSheet, startRowIdx: Int): Unit = {
    val writer = new TableWriter(table, sheet)
    writer.write(startRowIdx)
  }
}

class TableWriter(table: Table, sheet: XSSFSheet) {
  private var rowIdx = -1
  private var colIndex = -1
  private val merged = Collections.newSet[(Int, Int)]
  private val styles = Collections.newMap[String, XSSFCellStyle]
  private val fonts = Collections.newMap[String, XSSFFont]
  private val defaultStyle = buildDefaultStyle(sheet.getWorkbook)
  private var widths: Array[Length] = _

  private def buildDefaultStyle(wb: XSSFWorkbook): XSSFCellStyle = {
    val style = wb.createCellStyle
    style.setAlignment(HorizontalAlignment.CENTER)
    style.setVerticalAlignment(VerticalAlignment.CENTER)
    style.setWrapText(true)
    style
  }

  def write(startRowIdx: Int): Unit = {
    rowIdx = startRowIdx - 1
    widths = table.widths
    table.caption foreach { caption =>
      writeRow(sheet, caption.text, rowIdx, widths.length)
    }
    widths.indices foreach { idx =>
      val width = widths(idx)
      if (null != width) sheet.setColumnWidth(idx, (width.charNums * 256).intValue)
    }

    table.thead.foreach { thead =>
      thead.rows foreach { tr =>
        val row = createRow()
        colIndex = -1
        tr.computedStyle.height foreach { height =>
          row.setHeightInPoints(height.points.floatValue)
        }
        tr.cells foreach { td =>
          val cell = createCell(row)
          fillin(cell, td)
          createMergeRegin(rowIdx, colIndex, td)
        }
      }
    }
    sheet.createFreezePane(0, rowIdx + 1) //冻结所在行及其之前的行

    table.tbodies foreach { tbody =>
      tbody.rows foreach { tr =>
        val row = createRow()
        colIndex = -1
        tr.computedStyle.height foreach { height =>
          row.setHeightInPoints(height.points.floatValue)
        }
        tr.cells foreach { td =>
          val cell = createCell(row)
          fillin(cell, td)
          createMergeRegin(rowIdx, colIndex, td)
        }
      }
    }
    //set style of merged regions
    val regionsIter = sheet.getMergedRegions.iterator()
    while (regionsIter.hasNext) {
      val region = regionsIter.next()
      val cell = sheet.getRow(region.getFirstRow).getCell(region.getFirstColumn)
      val cs = cell.getCellStyle
      RegionUtil.setBorderTop(cs.getBorderTop, region, sheet)
      RegionUtil.setTopBorderColor(cs.getTopBorderColor, region, sheet)
      RegionUtil.setBorderRight(cs.getBorderRight, region, sheet)
      RegionUtil.setRightBorderColor(cs.getRightBorderColor, region, sheet)
      RegionUtil.setBorderBottom(cs.getBorderBottom, region, sheet)
      RegionUtil.setBottomBorderColor(cs.getBottomBorderColor, region, sheet)
      RegionUtil.setBorderLeft(cs.getBorderLeft, region, sheet)
      RegionUtil.setLeftBorderColor(cs.getLeftBorderColor, region, sheet)
    }
    //view or print
    table.attributes.get("data-repeating-rows") foreach { repeatRows =>
      sheet.setRepeatingRows(CellRangeAddress.valueOf(repeatRows))
    }
    table.attributes.get("data-zoom") foreach { zoom =>
      sheet.setZoom(zoom.toInt)
    }
    table.attributes.get("data-print-scale") foreach { scale =>
      sheet.getPrintSetup.setScale(scale.toShort)
    }
  }

  private def createRow(): XSSFRow = {
    rowIdx += 1
    sheet.createRow(rowIdx)
  }

  private def fillin(cell: Cell, td: Table.Cell): Unit = {
    val style = td.computedStyle
    val texts = td.text
    //设置行高
    style.height match
      case None =>
        if (td.rowspan == 1) {
          val lines = Strings.count(texts, '\n') + 1
          if (lines > 1) {
            val newHeight = lines * sheet.getDefaultRowHeightInPoints
            if (newHeight > cell.getRow.getHeightInPoints) {
              cell.getRow.setHeightInPoints(newHeight)
            }
          }
        }
      case Some(height) =>
        cell.getRow.setHeightInPoints(height.points.floatValue)

    //填充富文本字符串
    style.font.flatMap(_.asciiFont) match
      case None => cell.setCellValue(texts)
      case Some(asciiFont) =>
        val parts = splitText(texts, asciiFont, style.font.get)
        if (td.rowspan == 1 && style.height.isEmpty) { //不跨行，且没有指定高度的情况下
          val newLines = Math.ceil(texts.length * 1.0 / getCharNums(td))
          val newHeight = newLines * sheet.getDefaultRowHeightInPoints
          if (newHeight > cell.getRow.getHeightInPoints) {
            cell.getRow.setHeightInPoints(newHeight.floatValue)
          }
        }
        val str = new XSSFRichTextString(parts.map(_.value).mkString)
        var pos = 0
        parts foreach { part =>
          part.font foreach { f => str.applyFont(pos, pos + part.value.length, getOrCreateFont(f)) }
          pos += part.value.length
        }
        cell.setCellValue(str)

    // 设置样式和文字方向
    cell.setCellStyle(getOrCreateStyle(td, style))
  }

  private def getCharNums(td: Table.Cell): Int = {
    var charNums = 0d
    (0 until td.colspan) foreach { i =>
      if (widths(colIndex + i) != null) {
        charNums += widths(colIndex + i).charNums
      }
    }
    charNums.toInt
  }

  /** 拆分成西文和汉字不同的字体
   *
   * @param text
   * @param asciiFont
   * @param defaultFont
   * @return
   */
  private def splitText(text: String, asciiFont: Font, defaultFont: Font): Seq[FontText] = {
    if (Strings.isBlank(text)) {
      List.empty
    } else {
      val chars = text.toCharArray
      val parts = Collections.newBuffer[FontText]
      val buf = new StringBuilder()
      var isIdeographic = Character.isIdeographic(chars(0))
      chars foreach { c =>
        val nextIsIdeographic = Character.isIdeographic(c)
        if nextIsIdeographic == isIdeographic then
          buf.append(c)
        else
          parts.append(FontText(buf.toString(), Some(if isIdeographic then defaultFont else asciiFont)))
          buf.clear()
          buf.append(c)
          isIdeographic = nextIsIdeographic
      }
      if (buf.nonEmpty) parts.append(FontText(buf.toString(), Some(if isIdeographic then defaultFont else asciiFont)))
      parts.toSeq
    }
  }

  private def getOrCreateStyle(td: Table.Cell, style: Style): XSSFCellStyle = {
    styles.get(style.toString) match
      case None =>
        if (style.properties.isEmpty) {
          defaultStyle
        } else {
          val wb = sheet.getWorkbook
          val s = wb.createCellStyle
          val align = style.textAlign.getOrElse("left") match {
            case "center" => HorizontalAlignment.CENTER
            case "right" => HorizontalAlignment.RIGHT
            case _ => HorizontalAlignment.LEFT
          }
          s.setAlignment(align)

          val valign = style.verticalAlign.getOrElse("middle") match {
            case "top" => VerticalAlignment.TOP
            case "bottom" => VerticalAlignment.BOTTOM
            case _ => VerticalAlignment.CENTER
          }

          s.setVerticalAlignment(valign)
          s.setWrapText(true)
          style.border foreach { b =>
            Styles.convertBorder(b.top) foreach { d =>
              s.setBorderTop(d._1)
              s.setTopBorderColor(d._2)
            }
            Styles.convertBorder(b.right) foreach { d =>
              s.setBorderRight(d._1)
              s.setRightBorderColor(d._2)
            }
            Styles.convertBorder(b.bottom) foreach { d =>
              s.setBorderBottom(d._1)
              s.setBottomBorderColor(d._2)
            }
            Styles.convertBorder(b.left) foreach { d =>
              s.setBorderLeft(d._1)
              s.setLeftBorderColor(d._2)
            }
          }
          style.font foreach { f => s.setFont(getOrCreateFont(f)) }
          if (style.has("writing-mode", "vertical-rl") && style.has("text-orientation")) {
            s.setRotation(255)
          }
          styles.put(style.toString, s)
          s
        }
      case Some(st) => st
  }

  private def getOrCreateFont(f: Font): XSSFFont = {
    fonts.get(f.toString) match
      case None =>
        val font = sheet.getWorkbook.createFont
        f.family foreach { fm => font.setFontName(fm) }
        f.bold foreach { fm => font.setBold(true) }
        f.size foreach { s => font.setFontHeightInPoints(s) }
        f.strikeout foreach { s => font.setStrikeout(s) }
        Styles.convertUnderLine(f.underline) foreach { s => font.setUnderline(s) }
        fonts.put(f.toString, font)
        font
      case Some(font) => font
  }

  private def createCell(row: XSSFRow): XSSFCell = {
    colIndex += 1
    while (merged.contains((rowIdx, colIndex))) {
      colIndex += 1
    }
    row.createCell(colIndex)
  }

  private def createMergeRegin(rowIdx: Int, colIdx: Int, cell: Table.Cell): Unit = {
    if (cell.colspan > 1 || cell.rowspan > 1) {
      val mergedRegion = new CellRangeAddress(rowIdx, rowIdx + cell.rowspan - 1, colIdx, colIdx + cell.colspan - 1)
      sheet.addMergedRegion(mergedRegion)
    }
    if (cell.rowspan > 1) {
      for (r <- 1 until cell.rowspan; c <- colIndex until colIndex + cell.colspan) {
        merged.add(r + rowIdx, c)
      }
    }
    colIndex += cell.colspan - 1 //colIndex 最后一个写的位置，不是空位
  }

  private def writeRow(sheet: Sheet, content: String, rowIdx: Int, colSpan: Int): Cell = {
    val mergedRegion = new CellRangeAddress(rowIdx, rowIdx, 0, colSpan - 1)
    sheet.addMergedRegion(mergedRegion)

    val row = createRow()
    val cell = row.createCell(0)

    val newLines = Strings.count(content.trim(), "\n")
    if (newLines > 0) {
      row.setHeightInPoints((newLines + 1) * sheet.getDefaultRowHeightInPoints)
    }
    cell.setCellValue(content)
    cell
  }

}
