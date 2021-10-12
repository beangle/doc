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

import org.apache.poi.ss.usermodel.{CellStyle, Workbook}
import org.beangle.commons.collection.Collections
import org.beangle.commons.io.DataType
import org.beangle.commons.io.DataType.*
import org.beangle.doc.excel.ExcelStyleRegistry

object ExcelStyleRegistry {
  private val formats = Map(String -> "@", Boolean -> "@", Short -> "0", Integer -> "0", Long -> "#,##0",
    Float -> "#,##0.##", Double -> "#,##0.##",
    Date -> "YYYY-MM-D", Time -> "HH:MM:SS", DateTime -> "YYYY-MM-DD HH:MM:SS",
    ZonedDateTime -> "YYYY-MM-DD!THH:MM:SS!Z",
    Instant -> "YYYY-MM-DD!THH:MM:SS!Z",
    YearMonth -> "YYYY-MM", MonthDay -> "--MM-DD")

  def defaultFormat(dt: DataType): String = {
    formats.getOrElse(dt,"")
  }
}

class ExcelStyleRegistry(workbook: Workbook) {

  private val dataFormat = workbook.createDataFormat()

  private val styles = Collections.newMap[DataType, CellStyle]

  def get(dt: DataType): CellStyle = {
    styles(dt)
  }

  def registerFormat(dt: DataType, pattern: String): CellStyle = {
    val style = workbook.createCellStyle()
    style.setDataFormat(dataFormat.getFormat(pattern))
    styles.put(dt, style)
    style
  }

  DataType.values foreach { dt =>
    registerFormat(dt, ExcelStyleRegistry.defaultFormat(dt))
  }

}
