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

/** 纸张大小
 * 定义常规的纸张大小
 */
object PageSizes extends Enumeration(1) {

  class PageSize(val name: String) extends super.Val {
  }

  /**594 x 841 mm*/
  val A1 = new PageSize("A1")
  /** 420 x 594 mm */
  val A2 = new PageSize("A2")
  /** 297 x 420 mm */
  val A3 = new PageSize("A3")
  /** 210 x 297 mm, 8.26 x 11.69 inches */
  val A4 = new PageSize("A4")
  /** 148 x 210 mm */
  val A5 = new PageSize("A5")
  /** 105 x 148 mm */
  val A6 = new PageSize("A6")
  /** 8.5 x 11 inches, 215.9 x 279.4 mm */
  val Letter = new PageSize("Letter")
}
