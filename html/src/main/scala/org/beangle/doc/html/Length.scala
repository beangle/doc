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

import org.beangle.commons.lang.Numbers
import org.beangle.doc.html.Length.*

object Length {
  var PIXEL_DPI = 96
  var POINT_DPI = 72
  var DEFAULT_CHARACTER_WIDTH = 7.0017f;

  /** Parse 200pt,29mm to Length
   *
   * @param w
   * @return
   */
  def apply(w: String, defaultUnit: String = "px"): Length = {
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
      Length(w.toFloat, defaultUnit)
    } else {
      Length(w.substring(0, i).trim.toFloat, w.substring(i))
    }
  }

  def dxaToPoint(dxa: Int): Double = {
    dxa / 20
  }

  /** 到毫米的转换
   *
   * @param dxa
   * @return
   */
  def dxaToMM(dxa: Double): Length = {
    Length(Numbers.round(dxa / 20 * 0.353f, 2), "mm")
  }

  def dxa(v: Int): Length = {
    Length(v, "dxa")
  }
}

/** 长度
 * 72 == POINT_DPI
 * 96 == PIXEL_DPI
 *
 * @param num
 * @param unit
 */
case class Length(num: Double, unit: String) {

  def dxa: Int = {
    unit match
      //1英寸=72磅=25.4毫米=1440缇
      //1磅=0.353毫米=20缇
      //1毫米=56.7缇
      case "pt" => (num * 20).toInt
      case "in" => (num * 1440).toInt
      case "px" => (num / PIXEL_DPI * 1440).toInt
      case "mm" => (num / 0.353f * 20).toInt
      case "cm" => (num * 10 / 0.353f * 20).toInt
      case "dxa" => num.toInt
  }

  /** 转换成字符数
   *
   * @return
   */
  def charNums: Double = {
    unit match
      case "pt" => pointsToPixel(num) * 1.0f / DEFAULT_CHARACTER_WIDTH
      case "px" => num / DEFAULT_CHARACTER_WIDTH
      case "mm" => num / 25.4 * PIXEL_DPI / DEFAULT_CHARACTER_WIDTH
      case "cm" => num * 10 / 25.4 * PIXEL_DPI / DEFAULT_CHARACTER_WIDTH
      case _ => this.pixels / DEFAULT_CHARACTER_WIDTH
  }

  /** 转换成像素点(px)
   *
   * @return
   */
  def pixels: Int = {
    unit match
      case "pt" => pointsToPixel(num)
      case "px" => num.intValue
      case "mm" => (num / 25.4f * PIXEL_DPI).intValue
      case "cm" => (num * 10 / 25.4 * PIXEL_DPI).intValue
      case "dxa" => pointsToPixel(dxaToPoint(num.intValue))
      case "in" => pointsToPixel(num * 72)
  }

  /** 转换成磅(pt)
   *
   * @return
   */
  def points: Double = {
    unit match
      case "pt" => num
      case "px" => pixelToPoints(num)
      case "mm" => num / 25.4f * POINT_DPI
      case "cm" => num * 10 / 25.4f * POINT_DPI
      case "dxa" => dxaToPoint(num.intValue)
      case "in" => num * POINT_DPI
  }

  def mm: Double = {
    unit match
      case "pt" => num * 0.353f
      case "px" => pixelToPoints(num) * 0.353f
      case "mm" => num
      case "cm" => num * 10
      case "dxa" => dxaToMM(num).num
      case "in" => num * 25.4
  }

  def emus: Int = {
    (mm * 36000).intValue
  }

  def toMM: Length = {
    Length(this.mm, "mm")
  }

  def toPx: Length = {
    Length(this.pixels, "px")
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

  override def toString: String = {
    s"${num}${unit}"
  }
}
