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

package org.beangle.doc.excel.template.directive

import org.beangle.doc.excel.{CellRef, Size}
import org.beangle.doc.excel.template.{Area, Context}

object Directive {
  val INNER_SHIFT_MODE = "inner";
  val ADJACENT_SHIFT_MODE = "adjacent";
}

/**
 * A directive interface defines a transformation of a list of areas at a specified cell
 */
trait Directive {
  def name: String

  def areas: List[Area]

  def applyAt(cellRef: CellRef, context: Context): Size

  def reset(): Unit

  def setShiftMode(mode: String): Unit

  def shiftMode: String

  def setLockRange(isLock: String): Unit

  /**
   * Whether the area is locked
   * Other directives will no longer execute in this area after locking
   *
   * @return true or false (default true)
   */
  def lockRange: Boolean
}
