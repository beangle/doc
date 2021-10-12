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

import org.apache.poi.ss.usermodel.{PrintSetup, Sheet, Workbook}

object Sheets {

  def copyProperties(src: Sheet, dest: Sheet): Unit = {
    dest.setAutobreaks(src.getAutobreaks)
    dest.setDisplayGridlines(src.isDisplayGridlines)
    dest.setVerticallyCenter(src.getVerticallyCenter)
    dest.setFitToPage(src.getFitToPage)
    dest.setForceFormulaRecalculation(src.getForceFormulaRecalculation)
    dest.setRowSumsRight(src.getRowSumsRight)
    dest.setRowSumsBelow(src.getRowSumsBelow)
    copyPrintSetup(src, dest)
  }

  private def copyPrintSetup(src: Sheet, dest: Sheet): Unit = {
    val ss = src.getPrintSetup
    val ds = dest.getPrintSetup
    ds.setCopies(ss.getCopies)
    ds.setDraft(ss.getDraft)
    ds.setFitHeight(ss.getFitHeight)
    ds.setFitWidth(ss.getFitWidth)
    ds.setFooterMargin(ss.getFooterMargin)
    ds.setHeaderMargin(ss.getHeaderMargin)
    ds.setHResolution(ss.getHResolution)
    ds.setLandscape(ss.getLandscape)
    ds.setLeftToRight(ss.getLeftToRight)
    ds.setNoColor(ss.getNoColor)
    ds.setNoOrientation(ss.getNoOrientation)
    ds.setNotes(ss.getNotes)
    ds.setPageStart(ss.getPageStart)
    ds.setPaperSize(ss.getPaperSize)
    ds.setScale(ss.getScale)
    ds.setUsePage(ss.getUsePage)
    ds.setValidSettings(ss.getValidSettings)
    ds.setVResolution(ss.getVResolution)
  }

  def remove(workbook: Workbook, sheetName: String): Boolean = {
    val sheetIndex = workbook.getSheetIndex(sheetName)
    val existed = sheetIndex > -1
    if (existed) workbook.removeSheetAt(sheetIndex)
    existed
  }

  def hide(workbook: Workbook, sheetName: String): Unit = {
    val sheetIndex = workbook.getSheetIndex(sheetName)
    val existed = sheetIndex > -1
    if (existed) workbook.setSheetHidden(sheetIndex, true)
  }

}
