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

package org.beangle.doc.core

import org.beangle.commons.lang.Strings
import scala.math.BigDecimal.RoundingMode

object PrintOptions {
  def defaultOptions: PrintOptions = {
    new PrintOptions()
  }
}

class PrintOptions {
  /** 纸张大小 */
  var pageSize: PageSize = PageSize.A4
  /** 纸张方向 */
  var orientation: Orientation = Orientation.Portrait
  /** 是否打印背景 */
  var printBackground: Boolean = true
  /** 页边距 */
  var margin: PageMargin = PageMargin.Zero
  /** 缩放到页面宽度 */
  var shrinkToFit: Boolean = true
  /** 缩放比 */
  var scale: Double = 1.0
  /** 是否打印页眉页脚 */
  var printHeaderFooter: Boolean = _
  /** 打印拷贝数 */
  var copys: Int = 1
  /** 打印范围，例如1-5, 8, 11-13 */
  var pageRanges: Option[String] = None
  /** 是否缩小到一页 */
  var shrinkTo1Page: Boolean = false
}

/** 打印方向
 * Portrait 为纵向
 * Landscape 为横向
 */
enum Orientation(val id: Int, val name: String) {
  case Portrait extends Orientation(1, "Portrait")
  case Landscape extends Orientation(2, "Landscape")
}

object Orientation {
  def fromId(id: Int): Orientation = fromOrdinal(id - 1)
}

object Millimeter {
  def apply(m: String): Millimeter = {
    val s = m.toLowerCase
    if (s.endsWith("mm")) {
      new Millimeter(Strings.replace(s, "mm", "").toInt)
    } else {
      new Millimeter(s.toInt)
    }
  }

  given Conversion[Int, Millimeter] = Millimeter(_)
}

case class Millimeter(v: Int) {
  def mm: String = s"${v}mm"

  def inches: Double = {
    BigDecimal(v / 25.4d).setScale(3, RoundingMode.HALF_UP).doubleValue
  }

  def inches(postfix: String = "in"): String = {
    String.format("%.3f" + postfix, v / 25.4d)
  }

  override def toString: String = s"${v}mm"
}

import org.beangle.doc.core.Millimeter.given
import scala.language.implicitConversions
/** 纸张大小
 * 定义常规的纸张大小
 */
enum PageSize(val name: String, val width: Millimeter, val height: Millimeter) {
  /** 594 x 841 mm */
  case A1 extends PageSize("A1", 594, 841)
  /** 420 x 594 mm */
  case A2 extends PageSize("A2", 420, 594)
  /** 297 x 420 mm */
  case A3 extends PageSize("A3", 297, 420)
  /** 210 x 297 mm, 8.26 x 11.69 inches */
  case A4 extends PageSize("A4", 210, 297)
  /** 148 x 210 mm */
  case A5 extends PageSize("A5", 148, 210)
  /** 105 x 148 mm */
  case A6 extends PageSize("A6", 105, 148)
}

object PageMargin {
  val Default = PageMargin(10, 10, 10, 10)
  val Zero = PageMargin(0, 0, 0, 0)

  def apply(m: String): PageMargin = {
    if (m == "0mm" || m == "0") {
      PageMargin.Zero
    } else {
      val ms = Strings.split(m, ' ')
      if (ms.length == 2) {
        val tb = Millimeter(ms(0))
        val lr = Millimeter(ms(1))
        PageMargin(tb, tb, lr, lr)
      } else if (ms.length == 4) {
        PageMargin(Millimeter(ms(0)), Millimeter(ms(1)), Millimeter(ms(2)), Millimeter(ms(3)))
      } else {
        PageMargin.Default
      }
    }
  }
}

case class PageMargin(top: Millimeter, bottom: Millimeter, left: Millimeter, right: Millimeter)
