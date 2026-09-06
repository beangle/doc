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
import org.beangle.commons.lang.ClassLoaders
import org.beangle.doc.excel.template.directive.UpdateCellDirective
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.time.MonthDay
import scala.collection.mutable

/**
 * 模板渲染行为测试：用代码构造的模板验证 jx:area/jx:each/jx:if/jx:updateCell/jx:image
 * 等指令展开后的真实输出（行扩展、公式改写、合并区域、条件格式、多 sheet 等）。
 */
class TemplateRenderTest extends AnyFunSuite, Matchers {

  private val png1x1: Array[Byte] = java.util.Base64.getDecoder.decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/AAE2t9wAAAAASUVORK5CYII=")

  private val emps = List(
    Employee("Elsa", 28, 1500, 0.15, MonthDay.parse("--09-02")),
    Employee("Oleg", 32, 2300, 0.25, MonthDay.parse("--09-03")),
    Employee("Neil", 34, 2500, 0, MonthDay.parse("--09-04")))

  private def departments = {
    val it = Department("IT", emps.head, emps, "http://company.com/it")
    val maria = Employee("Maria", 34, 1700, 0.15, MonthDay.parse("--09-04"))
    val john = Employee("John", 35, 2800, 0.20, MonthDay.parse("--09-05"))
    val hr = Department("HR", emps.head, List(maria, john), "http://company.com/hr")
    List(it, hr)
  }

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

  private def render(bytes: Array[Byte], datas: collection.Map[String, Any]): XSSFWorkbook = {
    val out = new ByteArrayOutputStream()
    new TransformHelper(new ByteArrayInputStream(bytes)).transform(out, datas)
    new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray))
  }

  private def text(sheet: Sheet, row: Int, col: Int): String = {
    val rowObj = sheet.getRow(row)
    if (rowObj == null) return ""
    val cell = rowObj.getCell(col)
    if (cell == null) return ""
    cell.getCellType match {
      case CellType.STRING  => cell.getStringCellValue
      case CellType.NUMERIC => cell.getNumericCellValue.toString
      case CellType.FORMULA => cell.getCellFormula
      case _                => ""
    }
  }

  private def numeric(sheet: Sheet, row: Int, col: Int): Double = {
    val cell = sheet.getRow(row).getCell(col)
    cell.getNumericCellValue
  }

  private def formula(sheet: Sheet, row: Int, col: Int): String = {
    val cell = sheet.getRow(row).getCell(col)
    cell.getCellType shouldBe CellType.FORMULA
    cell.getCellFormula
  }

  private def hasMerge(sheet: Sheet, firstRow: Int, lastRow: Int, firstCol: Int, lastCol: Int): Boolean = {
    (0 until sheet.getNumMergedRegions).exists { i =>
      val region = sheet.getMergedRegion(i)
      region.getFirstRow == firstRow && region.getLastRow == lastRow &&
        region.getFirstColumn == firstCol && region.getLastColumn == lastCol
    }
  }

  test("each expands bean rows downward and rewrites user formulas") {
    val bytes = workbookBytes { wb =>
      val sheet = wb.createSheet("T")
      val header = sheet.createRow(0)
      header.createCell(0).setCellValue("name")
      header.createCell(1).setCellValue("double")
      val dataRow = sheet.createRow(1)
      dataRow.createCell(0).setCellValue("${e.name()}")
      dataRow.createCell(1).setCellValue("$[A2*2]")
      addComment(sheet, dataRow.getCell(0), "jx:area(lastCell=\"B2\")\njx:each(items=\"emps\", var=\"e\", lastCell=\"B2\")")
    }
    val sheet = render(bytes, mutable.HashMap("emps" -> emps)).getSheetAt(0)
    sheet.getLastRowNum shouldBe 3
    text(sheet, 0, 0) shouldBe "name"
    text(sheet, 0, 1) shouldBe "double"
    text(sheet, 1, 0) shouldBe "Elsa"
    text(sheet, 2, 0) shouldBe "Oleg"
    text(sheet, 3, 0) shouldBe "Neil"
    formula(sheet, 1, 1) shouldBe "A2*2"
    formula(sheet, 2, 1) shouldBe "A3*2"
    formula(sheet, 3, 1) shouldBe "A4*2"
  }

  test("if directive filters repeated rows by condition") {
    val bytes = workbookBytes { wb =>
      val sheet = wb.createSheet("T")
      val header = sheet.createRow(0)
      header.createCell(0).setCellValue("name")
      header.createCell(1).setCellValue("age")
      val dataRow = sheet.createRow(1)
      dataRow.createCell(0).setCellValue("${e.name()}")
      dataRow.createCell(1).setCellValue("${e.age()}")
      addComment(sheet, dataRow.getCell(0),
        "jx:area(lastCell=\"B2\")\njx:each(items=\"emps\", var=\"e\", lastCell=\"B2\")\n" +
          "jx:if(condition=\"e.age() >= 30\", lastCell=\"B2\")")
    }
    val sheet = render(bytes, mutable.HashMap("emps" -> emps)).getSheetAt(0)
    sheet.getLastRowNum shouldBe 2
    text(sheet, 1, 0) shouldBe "Oleg"
    numeric(sheet, 1, 1) shouldBe 32.0
    text(sheet, 2, 0) shouldBe "Neil"
    numeric(sheet, 2, 1) shouldBe 34.0
  }

  test("each duplicates scalar items into every row cell") {
    val bytes = workbookBytes { wb =>
      val sheet = wb.createSheet("T")
      val header = sheet.createRow(0)
      header.createCell(0).setCellValue("idx")
      header.createCell(1).setCellValue("val")
      val dataRow = sheet.createRow(1)
      dataRow.createCell(0).setCellValue("${item}")
      dataRow.createCell(1).setCellValue("${item}")
      addComment(sheet, dataRow.getCell(0), "jx:area(lastCell=\"B2\")\njx:each(items=\"datas\", var=\"item\", lastCell=\"B2\")")
    }
    val sheet = render(bytes, mutable.HashMap("datas" -> List("1", "2", "3", "a"))).getSheetAt(0)
    sheet.getLastRowNum shouldBe 4
    (1 to 4).foreach { r =>
      text(sheet, r, 0) shouldBe text(sheet, r, 1)
    }
    text(sheet, 1, 0) shouldBe "1"
    text(sheet, 2, 0) shouldBe "2"
    text(sheet, 3, 0) shouldBe "3"
    text(sheet, 4, 0) shouldBe "a"
  }

  test("each with direction Right expands horizontally") {
    val bytes = workbookBytes { wb =>
      val sheet = wb.createSheet("T")
      val header = sheet.createRow(0)
      header.createCell(0).setCellValue("start")
      val dataCell = sheet.createRow(1).createCell(0)
      dataCell.setCellValue("${item}")
      addComment(sheet, dataCell, "jx:area(lastCell=\"A2\")\njx:each(items=\"datas\", var=\"item\", lastCell=\"A2\", direction=\"Right\")")
    }
    val sheet = render(bytes, mutable.HashMap("datas" -> List("x", "y", "z"))).getSheetAt(0)
    sheet.getLastRowNum shouldBe 1
    text(sheet, 1, 0) shouldBe "x"
    text(sheet, 1, 1) shouldBe "y"
    text(sheet, 1, 2) shouldBe "z"
  }

  test("static footer row is shifted below expanded rows") {
    val bytes = workbookBytes { wb =>
      val sheet = wb.createSheet("T")
      val header = sheet.createRow(0)
      header.createCell(0).setCellValue("hdr")
      val dataRow = sheet.createRow(1)
      dataRow.createCell(0).setCellValue("${e.name()}")
      dataRow.createCell(1).setCellValue("$[A2*2]")
      val footer = sheet.createRow(2)
      footer.createCell(0).setCellValue("footer")
      addComment(sheet, dataRow.getCell(0), "jx:area(lastCell=\"C3\")\njx:each(items=\"emps\", var=\"e\", lastCell=\"B2\")")
    }
    val sheet = render(bytes, mutable.HashMap("emps" -> emps)).getSheetAt(0)
    sheet.getLastRowNum shouldBe 4
    text(sheet, 0, 0) shouldBe "hdr"
    text(sheet, 1, 0) shouldBe "Elsa"
    text(sheet, 2, 0) shouldBe "Oleg"
    text(sheet, 3, 0) shouldBe "Neil"
    text(sheet, 4, 0) shouldBe "footer"
  }

  test("multisheet each generates one sheet per item") {
    val depts = departments
    val bytes = workbookBytes { wb =>
      val sheet = wb.createSheet("Template")
      val row = sheet.createRow(0)
      row.createCell(0).setCellValue("${d.name()}")
      row.createCell(1).setCellValue("${d.headcount()}")
      addComment(sheet, row.getCell(0),
        "jx:area(lastCell=\"B1\")\njx:each(items=\"depts\", var=\"d\", lastCell=\"B1\", multisheet=\"sheetNames\")")
    }
    val wb = render(bytes, mutable.HashMap("depts" -> depts, "sheetNames" -> depts.map(_.name)))
    wb.getNumberOfSheets shouldBe 2
    wb.getSheetName(0) shouldBe "IT"
    wb.getSheetName(1) shouldBe "HR"
    val it = wb.getSheet("IT")
    text(it, 0, 0) shouldBe "IT"
    numeric(it, 0, 1) shouldBe 3.0
    val hr = wb.getSheet("HR")
    text(hr, 0, 0) shouldBe "HR"
    numeric(hr, 0, 1) shouldBe 2.0
  }

  test("each copies merged regions per row while keeping title merge") {
    val bytes = workbookBytes { wb =>
      val sheet = wb.createSheet("T")
      val title = sheet.createRow(0)
      title.createCell(0).setCellValue("title")
      sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2))
      val dataRow = sheet.createRow(1)
      dataRow.createCell(0).setCellValue("${e.name()}")
      dataRow.createCell(1).setCellValue("${e.age()}")
      dataRow.createCell(2).setCellValue("x")
      sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 1))
      addComment(sheet, dataRow.getCell(0), "jx:area(lastCell=\"C2\")\njx:each(items=\"emps\", var=\"e\", lastCell=\"C2\")")
    }
    val sheet = render(bytes, mutable.HashMap("emps" -> emps)).getSheetAt(0)
    sheet.getNumMergedRegions shouldBe 4
    hasMerge(sheet, 0, 0, 0, 2) shouldBe true
    hasMerge(sheet, 1, 1, 0, 1) shouldBe true
    hasMerge(sheet, 2, 2, 0, 1) shouldBe true
    hasMerge(sheet, 3, 3, 0, 1) shouldBe true
    text(sheet, 1, 0) shouldBe "Elsa"
    text(sheet, 2, 0) shouldBe "Oleg"
    text(sheet, 3, 0) shouldBe "Neil"
  }

  test("conditional formatting rules are copied to each expanded row") {
    val bytes = workbookBytes { wb =>
      val sheet = wb.createSheet("T")
      val header = sheet.createRow(0)
      header.createCell(0).setCellValue("name")
      val dataRow = sheet.createRow(1)
      dataRow.createCell(0).setCellValue("${e.name()}")
      addComment(sheet, dataRow.getCell(0), "jx:area(lastCell=\"A2\")\njx:each(items=\"emps\", var=\"e\", lastCell=\"A2\")")
      val cf = sheet.getSheetConditionalFormatting
      val rule = cf.createConditionalFormattingRule("A2<>\"\"")
      val pf = rule.createPatternFormatting()
      pf.setFillBackgroundColor(IndexedColors.YELLOW.index)
      cf.addConditionalFormatting(Array(new CellRangeAddress(1, 1, 0, 0)), rule)
    }
    val sheet = render(bytes, mutable.HashMap("emps" -> emps)).getSheetAt(0)
    val cf = sheet.getSheetConditionalFormatting
    val perRowRules = (0 until cf.getNumConditionalFormattings).flatMap { i =>
      val c = cf.getConditionalFormattingAt(i)
      if c.getFormattingRanges.length == 1 then Some((c.getFormattingRanges()(0), c.getRule(0).getFormula1))
      else None
    }
    perRowRules should contain((new CellRangeAddress(1, 1, 0, 0), "A2<>\"\""))
    perRowRules should contain((new CellRangeAddress(2, 2, 0, 0), "A2<>\"\""))
    perRowRules should contain((new CellRangeAddress(3, 3, 0, 0), "A2<>\"\""))
  }

  test("updateCell directive rewrites the target string value") {
    val bytes = workbookBytes { wb =>
      val sheet = wb.createSheet("T")
      val cell = sheet.createRow(1).createCell(0)
      cell.setCellValue("raw")
      addComment(sheet, cell, "jx:area(lastCell=\"A2\")\njx:updateCell(updater=\"up\", lastCell=\"A2\")")
    }
    val datas = new mutable.HashMap[String, Any]
    datas.put("up", new UpdateCellDirective.CellDataUpdater {
      override def updateCellData(cellData: CellData, targetCell: org.beangle.doc.excel.CellRef, context: Context): Unit = {
        cellData.cellValue = "changed:" + cellData.cellValue
      }
    })
    val sheet = render(bytes, datas).getSheetAt(0)
    text(sheet, 1, 0) shouldBe "changed:raw"
  }

  test("image directive inside area embeds a picture") {
    val bytes = workbookBytes { wb =>
      val sheet = wb.createSheet("T")
      val header = sheet.createRow(0)
      header.createCell(0).setCellValue("logo area")
      val dataRow = sheet.createRow(1)
      dataRow.createCell(0).setCellValue("${e.name()}")
      addComment(sheet, dataRow.getCell(0), "jx:area(lastCell=\"A2\")\njx:each(items=\"emps\", var=\"e\", lastCell=\"A2\")")
      val imageCell = sheet.createRow(3).createCell(0)
      addComment(sheet, imageCell, "jx:area(lastCell=\"B4\")\njx:image(src=\"logo\", lastCell=\"B4\")")
    }
    val wb = render(bytes, mutable.HashMap("emps" -> emps, "logo" -> png1x1))
    wb.getAllPictures.size shouldBe 1
  }

  test("shipped multisheet fixture renders department sheets with shifted formulas") {
    val it = departments.head
    val hr = departments(1)
    val ctx = mutable.HashMap[String, Any](
      "departments" -> List(it, hr),
      "sheetNames" -> List("IT", "HR"))
    val template = ClassLoaders.getResourceAsStream("multisheet_markup_template.xlsx").orNull
    val out = new ByteArrayOutputStream()
    new TransformHelper(template).transform(out, ctx)
    val wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray))
    wb.getNumberOfSheets shouldBe 2
    val itSheet = wb.getSheet("IT")
    text(itSheet, 1, 1) shouldBe "IT"
    text(itSheet, 6, 0) shouldBe "Employee"
    text(itSheet, 7, 0) shouldBe "Elsa"
    text(itSheet, 8, 0) shouldBe "Oleg"
    text(itSheet, 9, 0) shouldBe "Neil"
    numeric(itSheet, 7, 3) shouldBe 1500.0
    formula(itSheet, 7, 5) shouldBe "D8*(1+E8)"
    formula(itSheet, 8, 5) shouldBe "D9*(1+E9)"
    text(itSheet, 10, 0) shouldBe "TOTALS"
    formula(itSheet, 10, 3) shouldBe "SUM(D8:D10)"
    formula(itSheet, 10, 5) shouldBe "SUM(F8:F10)"
    val hrSheet = wb.getSheet("HR")
    text(hrSheet, 1, 1) shouldBe "HR"
    text(hrSheet, 7, 0) shouldBe "Maria"
    text(hrSheet, 8, 0) shouldBe "John"
    formula(hrSheet, 9, 3) shouldBe "SUM(D8:D9)"
    formula(hrSheet, 9, 5) shouldBe "SUM(F8:F9)"
  }
}
