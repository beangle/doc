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
enum PageSize(val name:String)  {

  /**594 x 841 mm*/
  case A1 extends PageSize("A1")
  /** 420 x 594 mm */
  case A2 extends PageSize("A2")
  /** 297 x 420 mm */
  case A3 extends PageSize("A3")
  /** 210 x 297 mm, 8.26 x 11.69 inches */
  case A4 extends PageSize("A4")
  /** 148 x 210 mm */
  case A5 extends PageSize("A5")
  /** 105 x 148 mm */
  case A6 extends PageSize("A6")
  /** 8.5 x 11 inches, 215.9 x 279.4 mm */
  case Letter extends PageSize("Letter")
}
