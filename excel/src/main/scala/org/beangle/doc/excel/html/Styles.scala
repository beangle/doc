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

import org.apache.poi.ss.usermodel.{BorderStyle, FontUnderline}
import org.apache.poi.xssf.usermodel.XSSFColor
import org.beangle.doc.html.dom.BorderData

object Styles {

  def convertBorder(data: BorderData): Option[(BorderStyle, XSSFColor)] = {
    if data.width == "0px" then None
    else
      val borderStyle =
        data.style match
          case "solid" =>
            if data.width == "1px" || data.width == "0.5px" then BorderStyle.THIN else BorderStyle.MEDIUM
          case "dashed" => if data.width == "1px" then BorderStyle.DOTTED else BorderStyle.MEDIUM_DASHED
          case "dotted" => BorderStyle.DOTTED
          case "double" => BorderStyle.DOUBLE
          case _ => BorderStyle.THIN
      val borderColor =
        if (data.color.startsWith("#") && data.color.length == 7) {
          val c = data.color
          new XSSFColor(Array[Byte](java.lang.Byte.valueOf(c.substring(1, 3)),
            java.lang.Byte.valueOf(c.substring(3, 5)),
            java.lang.Byte.valueOf(c.substring(5, 7))))
        } else {
          new XSSFColor(Array[Byte](0, 0, 0))
        }
      Some((borderStyle, borderColor))
  }

  def convertUnderLine(style: Option[String]): Option[FontUnderline] = {
    style match
      case Some("solid") => Some(FontUnderline.SINGLE)
      case Some("double") => Some(FontUnderline.DOUBLE)
      case _ => None
  }
}
