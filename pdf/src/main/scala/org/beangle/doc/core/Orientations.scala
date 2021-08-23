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
