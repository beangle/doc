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

import org.beangle.commons.collection.Collections
import org.beangle.commons.io.DataType
import org.beangle.commons.io.DataType.*
import org.beangle.commons.lang.{Numbers, Strings}
import org.beangle.doc.excel.ExcelStyleRegistry

import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.collection.mutable

/** Excel 导入格式
 *
 * 样式如下：
 * 0     title
 * 1    some remark
 * 2  column_1 remark,column_2 remark,...,column_n remark
 * 3  column_1 name,column_2 name,...,column_n name
 *
 */
class ExcelSchema {

  def createScheet(name: String = ""): ExcelScheet = {
    val sheetName =
      if (Strings.isBlank(name)) {
        "Sheet" + (sheets.size + 1)
      } else {
        name
      }
    val sheet = new ExcelScheet(sheetName)
    sheets += sheet
    sheet
  }

  def generate(os: OutputStream): Unit = {
    ExcelSchemaWriter.generate(this, os)
  }

  val sheets: mutable.Buffer[ExcelScheet] = Collections.newBuffer[ExcelScheet]
}

class ExcelScheet(var name: String) {

  var title: Option[String] = None
  var remark: Option[String] = None

  val columns: mutable.Buffer[ExcelColumn] = Collections.newBuffer[ExcelColumn]

  def title(t: String): this.type = {
    this.title = Some(t)
    this
  }

  def remark(r: String): this.type = {
    this.remark = Some(r)
    this
  }

  def add(name: String): ExcelColumn = {
    val column = new ExcelColumn(name)
    columns += column
    column
  }

  def add(name: String, comment: String): ExcelColumn = {
    val column = new ExcelColumn(name)
    columns += column
    column.comment = Some(comment)
    column
  }
}

class ExcelColumn(var name: String) {
  /** 批注 */
  var comment: Option[String] = None
  /** 说明 */
  var remark: Option[String] = None

  /** 数据类型 */
  var dataType: DataType = DataType.String
  /** 是否日期 */
  var isDate: Boolean = _
  /** 是否整形 */
  var isInt: Boolean = _
  /** 是否浮点型 */
  var isDecimal: Boolean = _
  /** 是否布尔型 */
  var isBool: Boolean = _

  /** 引用数据 */
  var refs: collection.Seq[String] = _
  /** 本列的数据(直接输出到本列的标题下方) */
  var datas: collection.Seq[String] = _

  /** 约束的第一个公式 */
  var formular1: String = _
  /** 约束的第一个公式 */
  var formular2: Option[String] = None

  /** 文本长度 */
  var length: Option[Int] = None
  /** 是否必须 */
  var required: Boolean = _
  /** 是否唯一 */
  var unique: Boolean = _

  /** 数据格式 */
  var format: Option[String] = None

  def format(f: String): this.type = {
    this.format = Some(f)
    this
  }

  def remark(r: String): this.type = {
    this.remark = Some(r)
    this
  }

  def unique(nv: Boolean = true): this.type = {
    this.unique = nv
    this
  }

  def required(r: Boolean = true): this.type = {
    this.required = r
    this
  }

  def date(f: String = "YYYY-MM-DD"): this.type = {
    isDate = true
    dataType = DataType.Date

    this.format = Some(f)
    val start = LocalDate.of(1900, 1, 1)
    formular1 = start.format(DateTimeFormatter.ofPattern(f))
    this
  }

  def min(formular: Any): this.type = {
    this.formular1 = formular.toString
    this
  }

  def max(formular: Any): this.type = {
    this.length = Some(Numbers.toInt(formular.toString))
    if (null == this.formular1) {
      this.formular1 = "0"
    }
    this.formular2 = Some(formular.toString)
    this
  }

  def length(max: Int): this.type = {
    length = Some(max)
    format = Some("@")
    if (null == formular1) {
      formular1 = "0"
    }
    formular2 = Some(max.toString)
    this
  }

  def decimal(f: String = "0.##"): this.type = {
    isDecimal = true
    dataType = DataType.Double
    format = Some(f)
    formular1 = "0"
    this
  }

  def decimal(min: Float, max: Float): this.type = {
    isDecimal = true
    dataType = DataType.Double
    format = Some("0.##")
    assert(max >= min)
    formular1 = min.toString
    formular2 = Some(max.toString)
    this
  }

  def bool(): this.type = {
    isBool = true
    dataType = DataType.Boolean
    this
  }

  def integer(f: String = "0"): this.type = {
    isInt = true
    dataType = DataType.Integer
    format = Some(f)
    formular1 = "0"
    this
  }

  def integer(min: Int, max: Int): this.type = {
    isInt = true
    dataType = DataType.Integer
    format = Some("0")
    assert(max >= min)
    formular1 = min.toString
    formular2 = Some(max.toString)
    this
  }

  def ref(data: collection.Seq[String]): this.type = {
    this.refs = data
    this
  }

  def data(data: collection.Seq[String]): this.type = {
    this.datas = data
    this
  }

  def asType(clazz: Class[_]): this.type = {
    asType(DataType.toType(clazz))
  }

  def asType(dt: DataType): this.type = {
    this.dataType = dt
    this.format = Some(ExcelStyleRegistry.defaultFormat(this.dataType))
    dt match {
      case Boolean => this.isBool = true
      case Short | Integer | Long =>
        this.isInt = true
        this.formular1 = "0"
      case Float | Double =>
        this.isDecimal = true
        this.formular1 = "0"
      case Date | Time | DateTime | ZonedDateTime | Instant | YearMonth | MonthDay =>
        this.isDate = true
        val start = LocalDate.of(1900, 1, 1)
        this.formular1 = start.format(DateTimeFormatter.ofPattern(this.format.get))
      case _ =>
    }
    this
  }
}
