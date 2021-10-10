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

import org.apache.poi.ss.usermodel.{ConditionalFormatting, ConditionalFormattingRule}
import org.apache.poi.ss.util.CellRangeAddress

import scala.collection.mutable

class PoiConditionalFormatting(val conditionalFormatting: ConditionalFormatting) {
  val ranges = mutable.ArraySeq.from(conditionalFormatting.getFormattingRanges)
  val rules = new mutable.ArrayBuffer[ConditionalFormattingRule]
  for (i <- 0 until conditionalFormatting.getNumberOfRules) {
    rules.addOne(conditionalFormatting.getRule(i))
  }

}
