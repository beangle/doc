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

import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.xssf.eventusermodel.{ReadOnlySharedStringsTable, XSSFReader}
import org.apache.poi.xssf.model.SharedStringsTable
import org.xml.sax.{Attributes, InputSource, XMLReader}
import org.xml.sax.helpers.DefaultHandler

import java.io.InputStream
import javax.xml.parsers.SAXParserFactory
import scala.collection.mutable

/** 流式 Excel 读取器 — 基于 POI SAX 事件流。
 *
 * 逐行读取 xlsx 文件，内存中只保留当前行数据，适合大文件读取。
 * 与 [[StreamingExcelWriter]] 对称：写用 SXSSFWorkbook，读用 SAX。
 *
 * <h3>基本用法</h3>
 * <pre>
 * Using.resource(new StreamingExcelReader(is)) { reader =>
 *   reader.readRow() // 跳过标题行
 *   var row = reader.readRow()
 *   while (row.isDefined) {
 *     process(row.get)
 *     row = reader.readRow()
 *   }
 * }
 * </pre>
 */
class StreamingExcelReader(is: InputStream, sheetNum: Int = 0) extends AutoCloseable {

  private val pkg = OPCPackage.open(is)
  private val strings = new ReadOnlySharedStringsTable(pkg)
  private val xssfReader = new XSSFReader(pkg)

  private val sheetIterator = xssfReader.getSheetsData.asInstanceOf[XSSFReader.SheetIterator]
  private var currentSheetStream: InputStream = _
  private val xmlReader: XMLReader = {
    val factory = SAXParserFactory.newInstance()
    factory.setNamespaceAware(true)
    factory.newSAXParser.getXMLReader
  }

  // 跳到指定 sheet
  private var sheetIndex = 0
  while (sheetIndex < sheetNum && sheetIterator.hasNext) {
    sheetIterator.next()
    sheetIndex += 1
  }
  if (sheetIterator.hasNext) {
    currentSheetStream = sheetIterator.next()
  }

  private val handler = new SheetHandler(strings)
  xmlReader.setContentHandler(handler)

  /** 读取下一行数据。返回 None 表示没有更多行。 */
  def readRow(): Option[Array[Any]] = {
    handler.reset()
    try {
      val source = new InputSource(currentSheetStream)
      xmlReader.parse(source)
    } catch {
      case _: org.xml.sax.SAXParseException =>
        // 到达 sheet 末尾
    }
    if (handler.hasRow) Some(handler.rowValues) else None
  }

  /** Sheet 名称 */
  def getSheetName: String = if (sheetIterator != null) sheetIterator.getSheetName else null

  override def close(): Unit = {
    try if (currentSheetStream != null) currentSheetStream.close()
    finally pkg.close()
  }
}

/** SAX 事件处理器 — 逐行解析 xlsx XML。 */
private class SheetHandler(strings: ReadOnlySharedStringsTable) extends DefaultHandler {

  private var cellValue = new StringBuilder
  private var cellType: String = _
  private var inValue = false
  private val rowBuffer = mutable.ArrayBuffer[Any]()
  private var colIndex = 0

  def hasRow: Boolean = rowBuffer.nonEmpty
  def rowValues: Array[Any] = rowBuffer.toArray

  def reset(): Unit = {
    rowBuffer.clear()
    colIndex = 0
  }

  override def startElement(uri: String, localName: String, qName: String, attributes: Attributes): Unit = {
    qName match {
      case "c" =>
        cellType = attributes.getValue("t")
        cellValue.setLength(0)
      case "v" | "t" => inValue = true
      case _ =>
    }
  }

  override def endElement(uri: String, localName: String, qName: String): Unit = {
    qName match {
      case "v" | "t" => inValue = false
      case "c" =>
        val value = cellValue.toString()
        rowBuffer += parseCellValue(value, cellType)
        colIndex += 1
      case "row" =>
        // 行结束，不清理 rowBuffer，等外部读取
      case _ =>
    }
  }

  override def characters(ch: Array[Char], start: Int, length: Int): Unit = {
    if (inValue) cellValue.append(ch, start, length)
  }

  private def parseCellValue(value: String, cellType: String): Any = {
    if (value.isEmpty) return null
    cellType match {
      case "s" =>
        // 共享字符串引用
        val idx = value.toInt
        strings.getItemAt(idx).getString
      case "b" =>
        value == "1"
      case "e" | null =>
        // 数值或内联字符串
        try value.toDouble catch { case _: NumberFormatException => value }
      case _ =>
        try value.toDouble catch { case _: NumberFormatException => value }
    }
  }
}
