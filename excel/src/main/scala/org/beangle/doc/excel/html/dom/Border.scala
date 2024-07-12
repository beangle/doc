package org.beangle.doc.excel.html.dom

import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.beangle.commons.lang.Strings

class BorderData(var width: String, var style: String, var color: String)

case class Border(properties: Map[String, String]) {
  private val _top = BorderData("0px", "none", "black")
  private val _right = BorderData("0px", "none", "black")
  private val _bottom = BorderData("0px", "none", "black")
  private val _left = BorderData("0px", "none", "black")

  private def updateData(data: BorderData, parts: Array[String]): Unit = {
    if (parts.length == 3) {
      data.width = parts(0)
      data.style = parts(1)
      data.color = parts(2)
    } else if (parts.length == 2) {
      data.width = parts(0)
      data.style = parts(1)
    } else if (parts.length == 1) {
      data.width = parts(0)
    }
  }

  properties.toSeq.sortBy(_._1) foreach { case (name, value) =>
    if (name == "border") {
      val parts = Strings.split(value, " ")
      updateData(_top, parts)
      updateData(_right, parts)
      updateData(_bottom, parts)
      updateData(_left, parts)
    } else if (name == "border-color") {
      _top.color = value
      _right.color = value
      _bottom.color = value
      _left.color = value
    } else if (name == "border-style") {
      _top.style = value
      _right.style = value
      _bottom.style = value
      _left.style = value
    } else if (name == "border-top") {
      updateData(_top, Strings.split(value, " "))
    } else if (name == "border-top-style") {
      _top.style = value
    } else if (name == "border-top-color") {
      _top.color = value
    } else if (name == "border-right") {
      updateData(_right, Strings.split(value, " "))
    } else if (name == "border-right-style") {
      _right.style = value
    } else if (name == "border-right-color") {
      _right.color = value
    } else if (name == "border-bottom") {
      updateData(_top, Strings.split(value, " "))
    } else if (name == "border-bottom-style") {
      _bottom.style = value
    } else if (name == "border-bottom-color") {
      _bottom.color = value
    } else if (name == "border-left") {
      updateData(_left, Strings.split(value, " "))
    } else if (name == "border-left-style") {
      _left.style = value
    } else if (name == "border-left-color") {
      _left.color = value
    }
  }

  private def convert(data: BorderData): Option[(BorderStyle, XSSFColor)] = {
    if data.width == "0px" then None
    else
      val borderStyle =
        data.style match
          case "solid" =>
            if data.width == "1px" then BorderStyle.THIN else BorderStyle.MEDIUM
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

  def top: Option[(BorderStyle, XSSFColor)] = {
    convert(_top)
  }

  def right: Option[(BorderStyle, XSSFColor)] = {
    convert(_right)
  }

  def bottom: Option[(BorderStyle, XSSFColor)] = {
    convert(_bottom)
  }

  def left: Option[(BorderStyle, XSSFColor)] = {
    convert(_left)
  }
}
