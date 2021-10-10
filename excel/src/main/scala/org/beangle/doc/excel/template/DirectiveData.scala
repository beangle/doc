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

package org.beangle.doc.excel.template

import org.beangle.doc.excel.{AreaRef, CellRef, Size}
import org.beangle.doc.excel.template.directive.Directive

object DirectiveData {

  def apply(areaRef: AreaRef, directive: Directive): DirectiveData = {
    val startCellRef = areaRef.firstCellRef
    val size = areaRef.size
    new DirectiveData(startCellRef, size, directive)
  }

  def apply(areaRef: String, directive: Directive): DirectiveData = {
    apply(AreaRef(areaRef), directive)
  }
}

class DirectiveData(var startCellRef: CellRef, val size: Size, val directive: Directive) {
  val initStartCellRef: CellRef = startCellRef

  def areaRef = AreaRef(startCellRef, size)

  def reset(): Unit = {
    resetStartCellAndSize()
    directive.reset()
  }

  def resetStartCellAndSize(): Unit = {
    this.startCellRef = initStartCellRef
  }

  override def toString:String={
    directive.name +"@" + startCellRef.toString
  }
}
