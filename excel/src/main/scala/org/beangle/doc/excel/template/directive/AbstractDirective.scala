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

import org.beangle.commons.lang.Strings.uncapitalize
import org.beangle.doc.excel.template.directive.AbstractDirective.*
import org.beangle.doc.excel.template.directive.Directive
import org.beangle.doc.excel.template.directive.Directive.*
import org.beangle.doc.excel.template.{Area, Transformer}
import org.slf4j.LoggerFactory

object AbstractDirective {
  val logger = LoggerFactory.getLogger(AbstractDirective.getClass)
}

/**
 * Implements basic directive methods and is a convenient base class
 */
abstract class AbstractDirective extends Directive {
  var areas: List[Area] = List.empty
  var shiftMode: String = null
  var lockRange = true

  override def name: String = {
    var sn = getClass.getSimpleName
    uncapitalize(sn.substring(0, sn.length - "Directive".length))
  }

  final def addArea(area: Area): Directive = {
    areas :+= area
    area.parentDirective = this
    this
  }

  override def reset(): Unit = {
    areas.foreach(_.reset())
  }

  override def setShiftMode(mode: String): Unit = {
    if (mode != null) if (mode.equalsIgnoreCase(INNER_SHIFT_MODE) || mode.equalsIgnoreCase(ADJACENT_SHIFT_MODE)) shiftMode = mode
    else logger.error("Cannot set cell shift mode to " + mode + " for directive: " + name)
  }

  override def setLockRange(isLock: String): Unit = {
    if (isLock != null && !("" == isLock)) this.lockRange = java.lang.Boolean.valueOf(isLock)
  }
}
