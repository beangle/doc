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

import org.beangle.doc.excel.template.*
import org.beangle.doc.excel.template.directive.{AbstractDirective, Directive}
import org.beangle.doc.excel.{CellRef, Size}

class IfDirective(var condition: String, a1: Area = Area.Empty, a2: Area = Area.Empty) extends AbstractDirective {
  super.addArea(if (a1 != null) a1 else Area.Empty)
  super.addArea(if (a2 != null) a2 else Area.Empty)

  override def applyAt(cellRef: CellRef, context: Context): Size = {
    val result = context.isTrue(condition)
    if (result) areas.head.applyAt(cellRef, context)
    else areas.tail.head.applyAt(cellRef, context)
  }
}
