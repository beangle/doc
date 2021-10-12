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

import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.{CellAddress, CellRangeAddress}
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.xssf.usermodel.{XSSFCell, XSSFSheet, XSSFWorkbook}
import org.beangle.commons.lang.Strings
import org.beangle.doc.excel.*
import org.beangle.doc.excel.CellOps.*
import org.beangle.doc.excel.template.DefaultTransformer.*
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTCell
import org.slf4j.{Logger, LoggerFactory}

import java.io.{IOException, InputStream, OutputStream}
import scala.collection.mutable

object DefaultTransformer {
  val logger = LoggerFactory.getLogger(classOf[DefaultTransformer])

  def createTransformer(is: InputStream): DefaultTransformer = {
    createTransformer(WorkbookFactory.create(is))
  }

  /**
   * Creates transformer instance from a {@link Workbook} instance
   *
   * @param workbook Excel template
   * @return transformer instance with the given workbook as template
   */
  def createTransformer(workbook: Workbook, streaming: Boolean = false): DefaultTransformer = {
    val wb =
      if streaming then
        workbook match {
          case xb: XSSFWorkbook => new SXSSFWorkbook(xb, SXSSFWorkbook.DEFAULT_WINDOW_SIZE, false, false)
          case _ => workbook
        }
      else workbook
    new DefaultTransformer(wb)
  }

  def createInitialContext: Context = {
    val context = new Context
//    context.putVar(POI_CONTEXT_KEY, Sheets)
    context
  }
}

class DefaultTransformer(val workbook: Workbook) extends AbstractTransformer {
  var lastCommentedColumn = 50

  override def isForwardOnly: Boolean = workbook.isInstanceOf[SXSSFWorkbook]

  sheetMap = readCellData()

  private def readCellData(): Map[String, SheetData] = {
    val numberOfSheets = workbook.getNumberOfSheets
    val sheets = new mutable.LinkedHashMap[String, SheetData]
    for (i <- 0 until numberOfSheets) {
      val sheet = workbook.getSheetAt(i)
      val sheetData = SheetData.createSheetData(sheet, this)
      sheets.put(sheetData.sheetName, sheetData)
    }
    sheets.toMap
  }

  override def transform(srcCellRef: CellRef, targetCellRef: CellRef, context: Context, updateRowHeightFlag: Boolean): Unit = {
    val cellData = isTransformable(srcCellRef, targetCellRef)
    if (cellData == null) return
    var destSheet = workbook.getSheet(targetCellRef.sheetName)
    if (destSheet == null) {
      destSheet = workbook.createSheet(targetCellRef.sheetName)
      Sheets.copyProperties(workbook.getSheet(srcCellRef.sheetName), destSheet)
    }
    var destRow = destSheet.getRow(targetCellRef.row)
    if (destRow == null) destRow = destSheet.createRow(targetCellRef.row)
    transformCell(srcCellRef, targetCellRef, context, updateRowHeightFlag, cellData, destSheet, destRow)
  }

  protected def isTransformable(srcCellRef: CellRef, targetCellRef: CellRef): CellData = {
    getCellData(srcCellRef) match {
      case Some(cd) => if targetCellRef == null || Strings.isBlank(targetCellRef.sheetName) then null else cd
      case None => null
    }
  }

  protected def transformCell(srcCell: CellRef, targetCell: CellRef, context: Context, updateRowHeightFlag: Boolean,
                              cellData: CellData, destSheet: Sheet, destRow: Row): Unit = {
    val sheetData = sheetMap(srcCell.sheetName)
    if (!ignoreColumnProps) destSheet.setColumnWidth(targetCell.col, sheetData.getColumnWidth(srcCell.col))
    if (updateRowHeightFlag && !ignoreRowProps) destRow.setHeight(sheetData.getRowData(srcCell.row).orNull.height.toShort)
    var destCell = destRow.getCell(targetCell.col)
    if (destCell == null) destCell = destRow.createCell(targetCell.col)
    try { // conditional formatting
      destCell.setBlank()
      cellData.writeToCell(destCell, context, this)
      copyMergedRegions(sheetData, cellData, targetCell)
    } catch {
      case e: Exception =>
        DefaultTransformer.logger.error("Failed to write a cell with {} and context keys {}", cellData, context.toMap.keySet, e)
    }
  }

  override def resetArea(areaRef: AreaRef): Unit = {
    removeMergedRegions(areaRef)
    removeConditionalFormatting(areaRef)
  }

  private def removeMergedRegions(areaRef: AreaRef): Unit = {
    val destSheet = workbook.getSheet(areaRef.sheetName)
    val numMergedRegions = destSheet.getNumMergedRegions
    for (i <- numMergedRegions until 0 by -1) {
      destSheet.removeMergedRegion(i - 1)
    }
  }

  // this method updates conditional formatting ranges only when the range is inside the passed areaRef
  private def removeConditionalFormatting(areaRef: AreaRef): Unit = {
    val destSheet: Sheet = workbook.getSheet(areaRef.sheetName)
    val areaRange: CellRangeAddress = CellRangeAddress.valueOf(areaRef.toString)
    val sheetConditionalFormatting: SheetConditionalFormatting = destSheet.getSheetConditionalFormatting
    val numConditionalFormattings: Int = sheetConditionalFormatting.getNumConditionalFormattings
    for (index <- 0 until numConditionalFormattings) {
      val conditionalFormatting: ConditionalFormatting = sheetConditionalFormatting.getConditionalFormattingAt(index)
      val ranges: Array[CellRangeAddress] = conditionalFormatting.getFormattingRanges
      val newRanges = new mutable.ArrayBuffer[CellRangeAddress]
      for (range <- ranges) {
        if (!areaRange.isInRange(range.getFirstRow, range.getFirstColumn) || !areaRange.isInRange(range.getLastRow, range.getLastColumn)) newRanges.addOne(range)
      }
      conditionalFormatting.setFormattingRanges(newRanges.toArray)
    }
  }

  final protected def copyMergedRegions(sheetData: SheetData, srcCellData: CellData, destCell: CellRef): Unit = {
    sheetData.mergedRegions.find(x => x.getFirstRow == srcCellData.row && x.getFirstColumn == srcCellData.col) foreach { r =>
      findAndRemoveExistingCellRegion(destCell)
      val destSheet = workbook.getSheet(destCell.sheetName)
      destSheet.addMergedRegion(new CellRangeAddress(destCell.row, destCell.row + r.getLastRow - r.getFirstRow, destCell.col, destCell.col + r.getLastColumn - r.getFirstColumn))
    }
  }

  final protected def findAndRemoveExistingCellRegion(cellRef: CellRef): Unit = {
    val destSheet: Sheet = workbook.getSheet(cellRef.sheetName)
    val numMergedRegions = destSheet.getNumMergedRegions
    var breaked = false
    for (i <- 0 until numMergedRegions; if !breaked) {
      val mergedRegion: CellRangeAddress = destSheet.getMergedRegion(i)
      if (mergedRegion.getFirstRow <= cellRef.row && mergedRegion.getLastRow >= cellRef.row &&
        mergedRegion.getFirstColumn <= cellRef.col && mergedRegion.getLastColumn >= cellRef.col) {
        destSheet.removeMergedRegion(i)
        breaked = true
      }
    }
  }

  override def setFormula(cellRef: CellRef, formulaString: String): Unit = {
    if (cellRef == null || cellRef.sheetName == null) return
    val cell = Workbooks.getOrCreateCell(workbook, cellRef)
    cell.setCellFormula(formulaString)
    cell.clearValue()
  }

  override def clearCell(cellRef: CellRef): Unit = {
    if (cellRef != null && null != cellRef.sheetName) {
      Workbooks.cleanCell(workbook, cellRef)
      findAndRemoveExistingCellRegion(cellRef)
    }
  }

  override def getCommentedCells: collection.Seq[CellData] = {
    val commentedCells = new mutable.ArrayBuffer[CellData]
    for (sheetData <- sheetMap.values; rowData <- sheetData; if rowData != null) {
      val row = rowData.row.getRowNum
      val cellDataList = readCommentsFromSheet(sheetData.sheet, row)
      commentedCells.addAll(cellDataList)
    }
    commentedCells
  }

  @throws[IOException]
  override def write(os: OutputStream): Unit = {
    workbook.write(os)
    os.close()
    dispose()
  }

  @throws[IOException]
  override def writeTo(os: OutputStream): Unit = {
    //if (!streaming && isEvaluateFormulas) workbook.getCreationHelper.createFormulaEvaluator.evaluateAll()
    workbook.write(os)
    dispose()
  }

  // http://poi.apache.org/components/spreadsheet/how-to.html#sxssf
  private def dispose(): Unit = {
    workbook match {
      case x: SXSSFWorkbook => x.dispose()
      case _ =>
    }
  }

  private def readCommentsFromSheet(sheet: Sheet, rowNum: Int): collection.Seq[CellData] = {
    val commentDataCells = new mutable.ArrayBuffer[CellData]
    for (i <- 0 to lastCommentedColumn) {
      val cellAddress = new CellAddress(rowNum, i)
      val comment = sheet.getCellComment(cellAddress)
      if (comment != null && comment.getString != null) {
        val cellData = CellData(new CellRef(sheet.getSheetName, rowNum, i), null)
        cellData.cellComment = comment.getString.getString
        commentDataCells.addOne(cellData)
      }
    }
    commentDataCells
  }

  override def updateRowHeight(srcSheetName: String, srcRowNum: Int, targetSheetName: String, targetRowNum: Int): Unit = {
    if (isForwardOnly) return
    val sheetData = sheetMap(srcSheetName)
    val rowData = sheetData.getRowData(srcRowNum).orNull
    var sheet = workbook.getSheet(targetSheetName)
    if (sheet == null) sheet = workbook.createSheet(targetSheetName)
    var targetRow = sheet.getRow(targetRowNum)
    if (targetRow == null) targetRow = sheet.createRow(targetRowNum)
    val srcHeight = if (rowData != null) rowData.height.toShort else sheet.getDefaultRowHeight
    targetRow.setHeight(srcHeight)
  }

  /**
   * @return xls = null, xlsx = XSSFWorkbook, xlsx with streaming = the inner XSSFWorkbook instance
   */
  private def getXSSFWorkbook: XSSFWorkbook = {
    workbook match {
      case sb: SXSSFWorkbook => sb.getXSSFWorkbook
      case xb: XSSFWorkbook => xb
      case _ => null
    }
  }

  override def adjustTableSize(ref: CellRef, size: Size): Unit = {
    val xwb = getXSSFWorkbook
    if (size.height > 0 && xwb != null) {
      val sheet = xwb.getSheet(ref.sheetName)
      if (sheet == null) DefaultTransformer.logger.error("Can not access sheet '{}'", ref.sheetName)
      else {
        import scala.jdk.javaapi.CollectionConverters.asScala
        for (table <- asScala(sheet.getTables)) {
          val areaRef = AreaRef(table.getSheetName + "!" + table.getCTTable.getRef)
          if (areaRef.contains(ref)) { // Make table higher
            areaRef.lastCellRef.row = ref.row + size.height - 1
            table.getCTTable.setRef(areaRef.firstCellRef.getCellName(true) + ":" + areaRef.lastCellRef.getCellName(true))
          }
        }
      }
    }
  }

  def getCellStyle(cellRef: CellRef): CellStyle = {
    val sheetData = sheetMap(cellRef.sheetName)
    sheetData.getCellData(cellRef).map(_.cellStyle).orNull
  }

  override def mergeCells(cellRef: CellRef, rows: Int, cols: Int): Unit = {
    Workbooks.mergeCells(workbook, cellRef, rows, cols, getCellStyle(cellRef))
  }
}
