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
import org.apache.poi.xssf.usermodel.{XSSFCell, XSSFRichTextString}
import org.beangle.commons.conversion.converter.String2BooleanConverter
import org.beangle.commons.io.DataType
import org.beangle.commons.lang.{Dates, Numbers, Strings}
import org.beangle.doc.excel.{CellOps, ExcelStyleRegistry}

import java.text.NumberFormat
import java.time.*
import java.time.temporal.Temporal

object CellOps {

  import scala.language.implicitConversions

  @inline implicit def toCell(x: Cell): CellOps = new CellOps(x)

  private val NumFormat = NumberFormat.getNumberInstance
  NumFormat.setMinimumFractionDigits(0)
  NumFormat.setGroupingUsed(false)
}

final class CellOps(private val cell: Cell) extends AnyVal {

  // protected so any user can change this piece of code
  def clearValue(): Unit = {
    cell match {
      case xc: XSSFCell =>
        val cell = xc.getCTCell // POI internal access, but there's no other way
        if (cell.isSetV) cell.unsetV()
      case _ =>
    }
  }

  def setComment(commentText: String, commentAuthor: String, a: ClientAnchor): Unit = {
    val sheet = cell.getSheet
    val wb = sheet.getWorkbook
    val drawing = sheet.createDrawingPatriarch
    val factory = wb.getCreationHelper
    val anchor =
      if (a == null) {
        val ca = factory.createClientAnchor
        ca.setCol1(cell.getColumnIndex + 1)
        ca.setCol2(cell.getColumnIndex + 3)
        ca.setRow1(cell.getRowIndex)
        ca.setRow2(cell.getRowIndex + 2)
        ca
      } else {
        a
      }
    val comment = drawing.createCellComment(anchor)
    comment.setString(factory.createRichTextString(commentText))
    comment.setAuthor(if (commentAuthor != null) commentAuthor else "")
    cell.setCellComment(comment)
  }

  /** 将值填入单元格中
   *
   * @param value    值
   * @param registry 样式注册表
   */
  def fillin(value: Any)(implicit registry: ExcelStyleRegistry): Unit = {
    val v =
      value match {
        case Some(s) => s
        case None => null
        case _ => value
      }
    v match {
      case null => fillBlank()
      case d: java.util.Date =>
        d match {
          case sd: java.sql.Date => fill(sd, registry)
          case st: java.sql.Timestamp => fill(st, registry)
          case stt: java.sql.Time => fill(stt, registry)
          case _ => fill(d, registry)
        }
      case uc: java.util.Calendar => fill(uc.getTime, registry)
      case t: Temporal =>
        t match {
          case ld: LocalDate => fill(java.sql.Date.valueOf(ld), registry)
          case i: Instant => fill(java.util.Date.from(i), registry)
          case ldt: LocalDateTime => fill(java.util.Date.from(ldt.atZone(ZoneId.systemDefault).toInstant), registry)
          case zdt: ZonedDateTime => fill(java.util.Date.from(zdt.toInstant), registry)
          case lt: LocalTime => fill(java.sql.Time.valueOf(lt), registry)
          case y: Year => fill(y, registry)
          case yt: YearMonth => fill(yt, registry)
        }
      case md: MonthDay => fill(md, registry)
      case n: Number =>
        n match {
          case i: Integer => fill(i.intValue(), registry)
          case f: java.lang.Float => fill(f.floatValue(), registry)
          case d: java.lang.Double => fill(d.doubleValue(), registry)
          case _ => fill(n.intValue(), registry)
        }
      case b: java.lang.Boolean => fill(b.booleanValue(), registry)
      case _ => fill(v.toString, registry)
    }
  }

  /** 取cell单元格中的数据
   *
   * @return
   */
  def getValue: Any = {
    cell.getCellType match {
      case CellType.BLANK => null
      case CellType.STRING => Strings.trim(cell.getRichStringCellValue.getString)
      case CellType.NUMERIC =>
        if (DateUtil.isCellDateFormatted(cell)) {
          cell.getDateCellValue
        } else {
          cell.getNumericCellValue
        }
      case CellType.BOOLEAN => if (cell.getBooleanCellValue) true else false
      case CellType.FORMULA =>
        cell.getCachedFormulaResultType match {
          case CellType.STRING => Strings.trim(cell.getRichStringCellValue.getString)
          case CellType.NUMERIC => cell.getNumericCellValue
          case _ => null
        }
      case _ => null
    }
  }

  /** 取cell单元格中的数据
   *
   * @param dataType 期望的数据类型
   * @return
   */
  def getValue(dataType: DataType): Any = {
    getValue match {
      case null => null
      case s: String => convert(s, dataType)
      case d: java.lang.Double => convert(d, dataType)
      case d: java.util.Date => convert(d, dataType)
      case d: Any => d
    }
  }

  private def convert(str: String, dataType: DataType): Any = {
    dataType match {
      case DataType.String => str
      case DataType.Boolean => String2BooleanConverter(str)
      case DataType.Short => Numbers.convert2Short(str)
      case DataType.Integer => Numbers.convert2Int(str)
      case DataType.Long => Numbers.convert2Long(str)
      case DataType.Float => Numbers.convert2Float(str)
      case DataType.Double => Numbers.convert2Double(str)
      case DataType.Date => LocalDate.parse(Dates.normalize(str))
      case DataType.DateTime => LocalDateTime.parse(Dates.normalize(str))
      case DataType.ZonedDateTime => ZonedDateTime.parse(str)
      case DataType.Instant => Instant.parse(str)
      case DataType.Time => LocalTime.parse(str)
      case DataType.YearMonth => YearMonth.parse(if (str.contains("-")) str.substring(0, 4) + "-" + str.substring(4) else str)
      case DataType.MonthDay => MonthDay.parse(if (str.startsWith("--")) "--" + str else str)
      case _ => str
    }
  }

  private def convert(d: Double, dataType: DataType): Any = {
    dataType match {
      case DataType.String => CellOps.NumFormat.format(d)
      case DataType.Short => d.shortValue
      case DataType.Integer => d.intValue
      case DataType.Long => d.longValue
      case DataType.Float => d.floatValue
      case DataType.Double => d
      case _ => throw new RuntimeException("Cannot convert double to  " + dataType)
    }
  }

  private def convert(d: java.util.Date, dataType: DataType): Any = {
    dataType match {
      case DataType.String => new java.sql.Date(d.getTime).toLocalDate.toString
      case DataType.Date => new java.sql.Date(d.getTime).toLocalDate
      case DataType.DateTime => d.toInstant.atZone(ZoneId.systemDefault).toLocalDateTime
      case DataType.Time => d.toInstant.atZone(ZoneId.systemDefault).toLocalTime
      case DataType.YearMonth => YearMonth.from(new java.sql.Date(d.getTime).toLocalDate)
      case DataType.MonthDay => MonthDay.from(new java.sql.Date(d.getTime).toLocalDate)
      case _ => throw new RuntimeException("Cannot convert date to  " + dataType)
    }
  }

  private def fill(d: java.sql.Date, registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(d)
    cell.setCellStyle(registry.get(DataType.Date))
  }

  private def fill(d: java.util.Date, registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(d)
    cell.setCellStyle(registry.get(DataType.DateTime))
  }

  private def fill(d: YearMonth, registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(java.sql.Date.valueOf(d.atDay(1)))
    cell.setCellStyle(registry.get(DataType.YearMonth))
  }

  private def fill(d: Year, registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(d.getValue)
    cell.setCellStyle(registry.get(DataType.YearMonth))
  }

  private def fill(d: MonthDay, registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(java.sql.Date.valueOf(d.atYear(2000)))
    cell.setCellStyle(registry.get(DataType.MonthDay))
  }

  private def fill(d: java.sql.Time, registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(d)
    cell.setCellStyle(registry.get(DataType.Time))
  }

  private def fill(d: Float, registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(d)
    cell.setCellStyle(registry.get(DataType.Float))
  }

  private def fill(d: Double, registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(d)
    cell.setCellStyle(registry.get(DataType.Double))
  }

  private def fill(d: Int, registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(d)
    cell.setCellStyle(registry.get(DataType.Integer))
  }

  private def fill(s: String, registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(new XSSFRichTextString(s))
  }

  private def fill(b: Boolean, registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(if (b) "Y" else "N")
  }

  private def fillBlank(): Unit = {
    cell.setBlank()
  }
}
