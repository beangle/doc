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
import org.beangle.commons.io.DataType
import org.beangle.commons.xml.{Document, Element, Node, Text}

import java.io.InputStream
import javax.xml.stream.{XMLInputFactory, XMLStreamConstants}
import scala.collection.mutable

/** 流式 Excel 读取器 — 基于 StAX 逐行解析，内存中只保留当前行。
 *
 * <h3>基本用法</h3>
 * <pre>
 * Using.resource(new StreamingReader(is)) { reader =>
 *   reader.forEachRow(row => process(row))
 * }
 * </pre>
 */
class StreamingReader(is: InputStream, sheetNum: Int = 0) extends AutoCloseable {

  private val pkg = OPCPackage.open(is)
  private val strings = new ReadOnlySharedStringsTable(pkg)
  private val xssfReader = new XSSFReader(pkg)

  private val sheetIterator = xssfReader.getSheetsData.asInstanceOf[XSSFReader.SheetIterator]
  private var currentSheetStream: InputStream = _

  private var sheetIndex = 0
  while (sheetIndex < sheetNum && sheetIterator.hasNext) {
    sheetIterator.next()
    sheetIndex += 1
  }
  if (sheetIterator.hasNext) {
    currentSheetStream = sheetIterator.next()
  }

  /** 当前 sheet 的单元格注释（cellRef → commentText）。首次调用时解析 xl/commentsN.xml。
   *
   * 注释格式：cellRef 使用 Excel 坐标（如 "A1"、"B3"），commentText 是注释文本。
   * 注释文本可用冒号分隔属性名和类型，如 "姓名:String"、"出生日期:Date"。
   *
   * 示例：
   * {{{
   * Map("A1" -> "姓名:String", "B1" -> "年龄:Integer", "C1" -> "出生日期:Date")
   * }}}
   *
   * 调用此方法后，可读取 comments 获取属性定义，并设置 skipRows 跳过注释行。
   */
  def comments: Map[String, String] = {
    if (_comments == null) _comments = parseComments()
    _comments
  }
  private var _comments: Map[String, String] = _

  // StAX reader
  private val staxReader = {
    val factory = XMLInputFactory.newInstance()
    factory.createXMLStreamReader(currentSheetStream)
  }
  private var reachedEnd = false

  /** 需要跳过的行数（含标题行/注释行）。默认 0。 */
  var skipRows: Int = 0

  /** 当前已读取的行号（0-based） */
  def currentRowNum: Int = _currentRowNum
  private var _currentRowNum: Int = 0

  def getSheetName: String = if (sheetIterator != null) sheetIterator.getSheetName else null

  /** 读取下一行数据，自动跳过前 skipRows 行。 */
  def readRow(): Option[Array[Any]] = {
    while (_currentRowNum < skipRows) {
      readRawRow()
      _currentRowNum += 1
    }
    val row = readRawRow()
    if (row.isDefined) _currentRowNum += 1
    row
  }

  /** 读取下一行数据并按指定类型转换。 */
  def readRow(types: Array[DataType]): Option[Array[Any]] = {
    readRow().map(_.zip(types).map { (value, dataType) => convertValue(value, dataType) })
  }

  /** 回调式流式处理：自动跳过前 skipRows 行，只对数据行调用 callback。 */
  def forEachRow(callback: Array[Any] => Unit): Unit = {
    var row = readRow()
    while (row.isDefined) {
      callback(row.get)
      row = readRow()
    }
  }

  override def close(): Unit = {
    try staxReader.close()
    finally {
      try if (currentSheetStream != null) currentSheetStream.close()
      finally pkg.close()
    }
  }

  private def readRawRow(): Option[Array[Any]] = {
    if (reachedEnd) return None
    val cells = mutable.ArrayBuffer[Any]()
    var inRow = false
    var done = false

    while (!done && staxReader.hasNext) {
      staxReader.next() match {
        case XMLStreamConstants.START_ELEMENT if staxReader.getLocalName == "row" =>
          inRow = true
          cells.clear()
        case XMLStreamConstants.START_ELEMENT if staxReader.getLocalName == "c" && inRow =>
          val cellType = staxReader.getAttributeValue(null, "t")
          cells += readCellValue(cellType)
        case XMLStreamConstants.END_ELEMENT if staxReader.getLocalName == "row" && inRow =>
          inRow = false
          done = true
        case XMLStreamConstants.END_ELEMENT if staxReader.getLocalName == "sheetData" =>
          reachedEnd = true
          done = true
        case _ =>
      }
    }
    if (cells.nonEmpty) Some(cells.toArray) else None
  }

  private def readCellValue(cellType: String): Any = {
    var value = ""
    var done = false
    while (!done && staxReader.hasNext) {
      staxReader.next() match {
        case XMLStreamConstants.CHARACTERS =>
          value += staxReader.getText
        case XMLStreamConstants.END_ELEMENT if staxReader.getLocalName == "c" =>
          done = true
        case _ =>
      }
    }
    parseCellValue(value.trim, cellType)
  }

  private def parseCellValue(value: String, cellType: String): Any = {
    if (value.isEmpty) return null
    cellType match {
      case "s" =>
        try {
          val idx = value.toInt
          strings.getItemAt(idx).getString
        } catch {
          case _: NumberFormatException => value
        }
      case "b" =>
        value == "1"
      case _ =>
        try value.toDouble catch { case _: NumberFormatException => value }
    }
  }

  private def convertValue(value: Any, dataType: DataType): Any = {
    if (value == null) return null
    dataType match {
      case DataType.String => value.toString
      case DataType.Integer => value match {
        case d: java.lang.Double => d.intValue()
        case n: Number => n.intValue()
        case s: String => try s.toInt catch { case _: Exception => s }
        case v => v.toString.toInt
      }
      case DataType.Long => value match {
        case d: java.lang.Double => d.longValue()
        case n: Number => n.longValue()
        case s: String => try s.toLong catch { case _: Exception => s }
        case v => v.toString.toLong
      }
      case DataType.Double => value match {
        case d: java.lang.Double => d
        case n: Number => n.doubleValue()
        case s: String => try s.toDouble catch { case _: Exception => s }
        case v => v.toString.toDouble
      }
      case DataType.Float => value match {
        case d: java.lang.Double => d.floatValue()
        case n: Number => n.floatValue()
        case s: String => try s.toFloat catch { case _: Exception => s }
        case v => v.toString.toFloat
      }
      case DataType.Boolean => value match {
        case b: java.lang.Boolean => b
        case s: String => s == "true" || s == "1" || s == "Y"
        case d: java.lang.Double => d != 0
        case v => v.toString.toBoolean
      }
      case _ => value.toString
    }
  }

  private def parseComments(): Map[String, String] = {
    val result = mutable.Map[String, String]()
    try {
      val commentsSuffix = s"comments${sheetNum + 1}.xml"
      val parts = pkg.getParts
      import scala.jdk.CollectionConverters.*
      val commentsPart = parts.asScala.find(_.getPartName.getName.endsWith(commentsSuffix)).orNull
      if (commentsPart != null) {
        val cis = commentsPart.getInputStream
        val bytes = cis.readAllBytes()
        cis.close()
        val doc = Document.parse(new java.io.ByteArrayInputStream(bytes))
        // XML 结构: <comments><commentList><comment ref="A1">...
        doc.children.find(_.label == "commentList").foreach { commentList =>
          commentList.children.filter(_.label == "comment").foreach { comment =>
            val ref = comment.get("ref").orNull
            if (ref != null) {
              val text = comment.children.map(extractText).mkString
              result(ref) = text
            }
          }
        }
      }
    } catch {
      case _: Exception =>
    }
    result.toMap
  }

  private def extractText(node: Node): String = node match {
    case e: Element => e.children.map(extractText).mkString
    case t: Text => t.text
    case _ => ""
  }
}
