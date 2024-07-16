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

package org.beangle.doc.excel

import org.apache.poi.ss.usermodel.{Cell, CellStyle, ClientAnchor, Workbook}
import org.apache.poi.ss.util.{CellAddress, CellRangeAddress}

object WorkbookOps {

  given Conversion[Workbook, WorkbookOps] = (wb: Workbook) => WorkbookOps(wb)
}

final class WorkbookOps(private val wb: Workbook) extends AnyVal {

  def cleanCell(cellRef: CellRef): Unit = {
    if (cellRef == null || cellRef.sheetName == null) return
    val sheet = wb.getSheet(cellRef.sheetName)
    if (sheet != null) {
      val row = sheet.getRow(cellRef.row)
      if (row != null) {
        var cell = row.getCell(cellRef.col)
        if (cell == null) {
          val cellAddress = new CellAddress(cellRef.row, cellRef.col)
          if (sheet.getCellComment(cellAddress) != null) {
            cell = row.createCell(cellRef.col)
            cell.removeCellComment()
          }
        } else {
          cell.setBlank()
          cell.setCellStyle(wb.getCellStyleAt(0))
          cell.removeCellComment()
        }
      }
    }
  }

  def getOrCreateCell(cellRef: CellRef): Cell = {
    require(cellRef != null && cellRef.sheetName != null)
    var sheet = wb.getSheet(cellRef.sheetName)
    if (sheet == null) sheet = wb.createSheet(cellRef.sheetName)
    var row = sheet.getRow(cellRef.row)
    if (row == null) row = sheet.createRow(cellRef.row)
    var cell = row.getCell(cellRef.col)
    if (cell == null) cell = row.createCell(cellRef.col)
    cell
  }

  def mergeCells(cellRef: CellRef, rows: Int, cols: Int, cellStyle: CellStyle = null): Unit = {
    val sheet = wb.getSheet(cellRef.sheetName)
    val region = new CellRangeAddress(cellRef.row, cellRef.row + rows - 1, cellRef.col, cellRef.col + cols - 1)
    sheet.addMergedRegion(region)
    if null != cellStyle then getOrCreateCell(cellRef).setCellStyle(cellStyle)
  }

  def addImage(areaRef: AreaRef, imageBytes: Array[Byte], imageType: ImageType, scaleX: Option[Double], scaleY: Option[Double]): Unit = {
    val pictureIdx = wb.addPicture(imageBytes, findPoiPictureTypeByImageType(imageType))
    addImage(areaRef, pictureIdx, scaleX, scaleY)
  }

  def addImage(areaRef: AreaRef, imageBytes: Array[Byte], imageType: ImageType): Unit = {
    val pictureIdx = wb.addPicture(imageBytes, findPoiPictureTypeByImageType(imageType))
    addImage(areaRef, pictureIdx, null, null)
  }

  private def addImage(areaRef: AreaRef, imageIdx: Int, scaleX: Option[Double], scaleY: Option[Double]): Unit = {
    val pictureResizeFlag = scaleX.nonEmpty && scaleY.nonEmpty
    val helper = wb.getCreationHelper
    var sheet = wb.getSheet(areaRef.sheetName)
    if (sheet == null) sheet = wb.createSheet(areaRef.sheetName)
    val drawing = sheet.createDrawingPatriarch
    val anchor = helper.createClientAnchor
    anchor.setCol1(areaRef.firstCellRef.col)
    anchor.setRow1(areaRef.firstCellRef.row)
    if (pictureResizeFlag) {
      anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE)
      anchor.setCol2(-1)
      anchor.setRow2(-1)
    } else {
      anchor.setCol2(areaRef.lastCellRef.col)
      anchor.setRow2(areaRef.lastCellRef.row)
    }
    val picture = drawing.createPicture(anchor, imageIdx)
    if (pictureResizeFlag) picture.resize(scaleX.get, scaleY.get)
  }

  private def findPoiPictureTypeByImageType(imageType: ImageType): Int = {
    imageType match {
      case ImageType.PNG => Workbook.PICTURE_TYPE_PNG
      case ImageType.JPEG => Workbook.PICTURE_TYPE_JPEG
    }
  }

}
