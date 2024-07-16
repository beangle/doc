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

package org.beangle.doc.html.dom

import org.beangle.doc.html.dom.Length.*

object Length {
  var PIXEL_DPI = 96
  var POINT_DPI = 72
  var DEFAULT_CHARACTER_WIDTH = 7.0017f;

  def apply(w: String): Length = {
    val chars = w.toCharArray
    var i = 0
    var c = chars(i)
    while ((c == ' ' || c == '.' || '0' <= c && c <= '9') && i < chars.length) {
      i += 1
      if (i < chars.length) {
        c = chars(i)
      }
    }
    if (i == chars.length) {
      Length(w.toFloat, "px")
    } else {
      Length(w.substring(0, i).trim.toFloat, w.substring(i))
    }
  }
}

/** 长度
 * 72== POINT_DPI
 * 96== PIXEL_DPI
 *
 * @param num
 * @param unit
 */
case class Length(num: Double, unit: String) {

  def twips: Int = {
    unit match
      //1英寸=72磅=25.4毫米=1440缇
      //1磅=0.353毫米=20缇
      case "pt" => (num * 20).toInt
      case "px" => (num / PIXEL_DPI * 1440).toInt
      case _ => num.toInt
  }

  def charNums: Double = {
    unit match
      case "pt" => pointsToPixel(num) * 1.0f / DEFAULT_CHARACTER_WIDTH
      case "px" => num / DEFAULT_CHARACTER_WIDTH
      case "mm" => num / 25.4 * PIXEL_DPI / DEFAULT_CHARACTER_WIDTH
      case "cm" => num * 10 / 25.4 * PIXEL_DPI / DEFAULT_CHARACTER_WIDTH
      case _ => num
  }

  def pixels: Int = {
    unit match
      case "pt" => pointsToPixel(num)
      case "px" => num.intValue()
      case "mm" => (num / 25.4f * PIXEL_DPI).intValue
      case "cm" => (num * 10 / 25.4 * PIXEL_DPI).intValue
      case _ => num.toInt
  }

  def points: Double = {
    unit match
      case "pt" => num
      case "px" => pixelToPoints(num)
      case "mm" => num / 25.4 * POINT_DPI
      case "cm" => num * 10 / 25.4f * POINT_DPI
      case _ => num
  }

  private def pointsToPixel(points: Double): Int = {
    var pixel = points
    pixel *= PIXEL_DPI
    pixel /= POINT_DPI
    Math.rint(pixel).toInt
  }

  private def pixelToPoints(pixel: Double): Double = {
    var points = pixel
    points *= POINT_DPI
    points /= PIXEL_DPI
    points
  }
}
