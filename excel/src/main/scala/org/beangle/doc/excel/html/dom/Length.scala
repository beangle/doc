package org.beangle.doc.excel.html.dom

import org.apache.poi.util.Units

object Length {
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
 * 72== Units.POINT_DPI
 * 96== Units.PIXEL_DPI
 *
 * @param num
 * @param unit
 */
case class Length(num: Double, unit: String) {

  def twips: Int = {
    unit match
      //1英寸=72磅=25.4毫米=1440缇
      //1磅=0.353毫米=20缇
      case "pt" => (num * org.apache.poi.ss.usermodel.Font.TWIPS_PER_POINT).toInt
      case "px" => (num.asInstanceOf[Double] / Units.PIXEL_DPI * 1440).toInt
      case _ => num.toInt
  }

  def charNums: Double = {
    unit match
      case "pt" => Units.pointsToPixel(num) * 1.0f / Units.DEFAULT_CHARACTER_WIDTH
      case "px" => num / Units.DEFAULT_CHARACTER_WIDTH
      case "mm" => num / 25.4 * Units.PIXEL_DPI / Units.DEFAULT_CHARACTER_WIDTH
      case "cm" => num * 10 / 25.4 * Units.PIXEL_DPI / Units.DEFAULT_CHARACTER_WIDTH
      case _ => num
  }

  def pixels: Int = {
    unit match
      case "pt" => Units.pointsToPixel(num)
      case "px" => num.intValue()
      case "mm" => (num / 25.4f * Units.PIXEL_DPI).intValue
      case "cm" => (num * 10 / 25.4 * Units.PIXEL_DPI).intValue
      case _ => num.toInt
  }

  def points: Double = {
    unit match
      case "pt" => num
      case "px" => Units.pixelToPoints(num)
      case "mm" => num / 25.4 * Units.POINT_DPI
      case "cm" => num * 10 / 25.4f * Units.POINT_DPI
      case _ => num
  }
}
