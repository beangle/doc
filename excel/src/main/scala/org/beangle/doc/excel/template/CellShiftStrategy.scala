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

import org.beangle.doc.excel.CellRef

trait CellShiftStrategy {
  def requiresColShifting(cell: CellRef, startRow: Int, endRow: Int, startColShift: Int): Boolean

  def requiresRowShifting(cell: CellRef, startCol: Int, endCol: Int, startRowShift: Int): Boolean
}

object CellShiftStrategy {
  object Adjacent extends CellShiftStrategy {
    override def requiresColShifting(cell: CellRef, startRow: Int, endRow: Int, startColShift: Int) = {
      cell != null && cell.col > startColShift
    }

    override def requiresRowShifting(cell: CellRef, startCol: Int, endCol: Int, startRowShift: Int) = {
      cell != null && cell.row > startRowShift
    }
  }

  object Inner extends CellShiftStrategy {
    override def requiresColShifting(cell: CellRef, startRow: Int, endRow: Int, startColShift: Int) = {
      cell != null && cell.col > startColShift && cell.row >= startRow && cell.row <= endRow
    }

    override def requiresRowShifting(cell: CellRef, startCol: Int, endCol: Int, startRowShift: Int) = {
      cell != null && cell.row > startRowShift && cell.col >= startCol && cell.col <= endCol
    }
  }

}
