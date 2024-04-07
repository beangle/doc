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
import org.beangle.doc.excel.template.*
import org.beangle.doc.excel.template.directive.UpdateCellDirective.*
import org.slf4j.{Logger, LoggerFactory}

object UpdateCellDirective {
  val logger = LoggerFactory.getLogger(UpdateCellDirective.getClass)

  trait CellDataUpdater {
    def updateCellData(cellData: CellData, targetCell: CellRef, context: Context): Unit
  }

}

class UpdateCellDirective(area: Area) extends AbstractDirective {
  var updater: String = null
  var cellDataUpdater: CellDataUpdater = null

  super.addArea(area)
  if ((area.size.height != 1) && (area.size.width != 1)) throw new IllegalArgumentException("You can only add a single cell area to updateCell")

  override def applyAt(cellRef: CellRef, context: Context): Size = {
    val cellDataUpdater = createCellDataUpdater(context)
    val area = areas.head
    if (cellDataUpdater != null) {
      val srcCell = area.startCellRef
      area.transformer.getCellData(srcCell) foreach { cd =>
        cellDataUpdater.updateCellData(cd, cellRef, context)
      }
    }
    area.applyAt(cellRef, context)
  }

  private def createCellDataUpdater(context: Context): CellDataUpdater = {
    if (updater == null) {
      logger.warn("Attribute 'updater' is not set!")
      return null
    }
    val updaterInstance = context.getVar(updater)
    if (updaterInstance.isInstanceOf[CellDataUpdater]) updaterInstance.asInstanceOf[CellDataUpdater]
    else {
      logger.warn("CellDataUpdater is null or does not implement CellDataUpdater! Attribute 'updater': {}", updater)
      null
    }
  }
}
