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

package org.beangle.doc.html

import org.beangle.commons.lang.Strings

class BorderData(val side: String, var width: String, var style: String, var color: String) {
  def toCss: String = {
    if (Strings.isEmpty(side)) {
      s"border:${width} ${style} ${color};"
    } else {
      s"border-${side}:${width} ${style} ${color};"
    }
  }

  def at(newSide: String): BorderData = {
    new BorderData(newSide, width, style, color)
  }
}

object Border {
  def apply(properties: Map[String, String]): Border = {
    val top = BorderData("top", "0px", "none", "black")
    val right = BorderData("right", "0px", "none", "black")
    val bottom = BorderData("bottom", "0px", "none", "black")
    val left = BorderData("left", "0px", "none", "black")

    properties.toSeq.sortBy(_._1) foreach { case (name, value) =>
      if (name == "border") {
        val parts = Strings.split(value, " ")
        updateData(top, parts)
        updateData(right, parts)
        updateData(bottom, parts)
        updateData(left, parts)
      } else if (name == "border-color") {
        top.color = value
        right.color = value
        bottom.color = value
        left.color = value
      } else if (name == "border-style") {
        top.style = value
        right.style = value
        bottom.style = value
        left.style = value
      } else if (name == "border-top") {
        updateData(top, Strings.split(value, " "))
      } else if (name == "border-top-style") {
        top.style = value
      } else if (name == "border-top-color") {
        top.color = value
      } else if (name == "border-right") {
        updateData(right, Strings.split(value, " "))
      } else if (name == "border-right-style") {
        right.style = value
      } else if (name == "border-right-color") {
        right.color = value
      } else if (name == "border-bottom") {
        updateData(top, Strings.split(value, " "))
      } else if (name == "border-bottom-style") {
        bottom.style = value
      } else if (name == "border-bottom-color") {
        bottom.color = value
      } else if (name == "border-left") {
        updateData(left, Strings.split(value, " "))
      } else if (name == "border-left-style") {
        left.style = value
      } else if (name == "border-left-color") {
        left.color = value
      }
    }
    val border = new Border(top, right, bottom, left)
    border.properties = properties
    border
  }

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

}

class Border(var top: BorderData, var right: BorderData, var bottom: BorderData, var left: BorderData) {
  var properties: Map[String, String] = Map.empty

  def update(style: String): Border = {
    Style.parse(style) foreach { case (k, v) =>
      properties = properties.updated(k, v)
    }
    this
  }
}
