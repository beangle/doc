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

import org.apache.poi.ss.util.WorkbookUtil
import org.slf4j.LoggerFactory

import scala.collection.mutable

trait SheetNameBuilder {
  def createSheetName(givenSheetName: String, index: Int): String
}

class DefaultSheetNameBuilder extends SheetNameBuilder {
  private val logger = LoggerFactory.getLogger(classOf[DefaultSheetNameBuilder])
  private val usedSheetNames  = new mutable.HashSet[String]

  override def createSheetName(givenSheetName: String, index: Int): String = {
    val sheetName = WorkbookUtil.createSafeSheetName(givenSheetName)
    var serialNumber = 1
    var newName = sheetName
    while (usedSheetNames.contains(newName)) { // until unique
      var len = sheetName.length
      var nameWithNumber: String = null
      while {
        len -= 1
        nameWithNumber = addSerialNumber(sheetName.substring(0, len + 1), serialNumber)
        newName = WorkbookUtil.createSafeSheetName(nameWithNumber)
        !(newName == nameWithNumber)
      } do ()
      serialNumber += 1
    }
    if (!(givenSheetName == newName)) {
      logger.info("Change invalid sheet name {} to {}", givenSheetName, sheetName)
    }
    usedSheetNames.addOne(newName)
    newName
  }

  protected def addSerialNumber(text: String, serialNumber: Int): String = {
    return text + "(" + serialNumber + ")"
  }
}
