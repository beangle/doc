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
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.time.LocalDate
import scala.util.Using

class TransformerRegressionTest extends AnyFunSuite, Matchers {

  private def addComment(sheet: Sheet, cell: Cell, text: String): Unit = {
    val drawing = sheet.createDrawingPatriarch
    val anchor = sheet.getWorkbook.getCreationHelper.createClientAnchor()
    val comment = drawing.createCellComment(anchor)
    comment.setString(sheet.getWorkbook.getCreationHelper.createRichTextString(text))
    comment.setAuthor("t")
    cell.setCellComment(comment)
  }

  private def workbookBytes(setup: XSSFWorkbook => Unit): Array[Byte] = {
    val wb = new XSSFWorkbook()
    try {
      setup(wb)
      val bos = new ByteArrayOutputStream()
      wb.write(bos)
      bos.toByteArray
    } finally wb.close()
  }

  private def transform(bytes: Array[Byte]): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    new TransformHelper(new ByteArrayInputStream(bytes)).transform(out, Map.empty[String, Any])
    out.toByteArray
  }

  test("static date cell inside jx area keeps its value") {
    val bytes = workbookBytes { wb =>
      val sheet = wb.createSheet("template")
      val row = sheet.createRow(2)
      val cell = row.createCell(1)
      cell.setCellValue(java.sql.Date.valueOf(LocalDate.of(2024, 1, 1)))
      val style = wb.createCellStyle()
      style.setDataFormat(wb.createDataFormat().getFormat("yyyy-MM-dd"))
      cell.setCellStyle(style)
      addComment(sheet, cell, "jx:area(lastCell=\"B3\")")
    }
    val expected = DateUtil.getExcelDate(java.sql.Date.valueOf(LocalDate.of(2024, 1, 1)))
    Using.resource(new XSSFWorkbook(new ByteArrayInputStream(transform(bytes)))) { wb =>
      val outCell = wb.getSheetAt(0).getRow(2).getCell(1)
      outCell.getCellType shouldBe CellType.NUMERIC
      outCell.getNumericCellValue shouldBe expected
    }
  }

  test("formula referencing a missing template cell does not fail") {
    val bytes = workbookBytes { wb =>
      val sheet = wb.createSheet("template")
      val a1 = sheet.createRow(0).createCell(0)
      a1.setCellValue("label")
      val row = sheet.createRow(1)
      row.createCell(2).setCellFormula("B9*2")
      addComment(sheet, a1, "jx:area(lastCell=\"C2\")")
    }
    Using.resource(new XSSFWorkbook(new ByteArrayInputStream(transform(bytes)))) { wb =>
      val outCell = wb.getSheetAt(0).getRow(1).getCell(2)
      outCell should not be null
      Set(CellType.FORMULA, CellType.STRING) should contain(outCell.getCellType)
    }
  }

  test("merged region outside the area is preserved") {
    val bytes = workbookBytes { wb =>
      val sheet = wb.createSheet("template")
      sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2))
      val title = sheet.createRow(0).createCell(0)
      title.setCellValue("title")
      val b2 = sheet.createRow(1).createCell(1)
      b2.setCellValue("b2")
      val c3 = sheet.createRow(2).createCell(2)
      c3.setCellValue("c3")
      addComment(sheet, b2, "jx:area(lastCell=\"C3\")")
    }
    Using.resource(new XSSFWorkbook(new ByteArrayInputStream(transform(bytes)))) { wb =>
      val sheet = wb.getSheetAt(0)
      val keepsTitleMerge = (0 until sheet.getNumMergedRegions).exists { i =>
        val region = sheet.getMergedRegion(i)
        region.getFirstRow == 0 && region.getLastRow == 0 && region.getFirstColumn == 0 && region.getLastColumn == 2
      }
      keepsTitleMerge shouldBe true
      sheet.getRow(1).getCell(1).getStringCellValue shouldBe "b2"
    }
  }

  test("directive comment beyond column 50 is discovered") {
    val wb = new XSSFWorkbook()
    try {
      val sheet = wb.createSheet("template")
      val cell = sheet.createRow(0).createCell(60)
      cell.setCellValue("x")
      addComment(sheet, cell, "jx:area(lastCell=\"BI1\")")
      val transformer = DefaultTransformer.createTransformer(wb)
      val commented = transformer.getCommentedCells
      commented.exists(cd => cd.cellRef.col == 60) shouldBe true
    } finally wb.close()
  }
}
