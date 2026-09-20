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

package org.beangle.doc.excel.stream

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.beangle.commons.io.DataType
import org.beangle.commons.lang.ClassLoaders
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.time.*
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.util.Using

class StreamingReaderTest extends AnyFunSuite with Matchers {

  private val sampleData: Seq[Array[Any]] = Seq(
    Array[Any]("Alice", 30.0, true),
    Array[Any]("Bob", 25.0, false),
    Array[Any]("Cathy", 35.0, true)
  )

  /** 在内存中生成多行测试数据，避免依赖只有单行的模板文件 sample.xlsx。 */
  private val sampleBytes: Array[Byte] = {
    val workbook = new XSSFWorkbook()
    try {
      val sheet = workbook.createSheet("sample")
      sampleData.zipWithIndex.foreach { (values, rowNum) =>
        val row = sheet.createRow(rowNum)
        values.zipWithIndex.foreach { (value, colNum) =>
          val cell = row.createCell(colNum)
          value match {
            case s: String  => cell.setCellValue(s)
            case d: Double  => cell.setCellValue(d)
            case b: Boolean => cell.setCellValue(b)
            case _          => cell.setCellValue(value.toString)
          }
        }
      }
      val bos = new ByteArrayOutputStream()
      workbook.write(bos)
      bos.toByteArray
    } finally workbook.close()
  }

  private def openReader(): StreamingReader = {
    new StreamingReader(new ByteArrayInputStream(sampleBytes))
  }

  /** 手工拼装最小 xlsx 字节，用于覆盖 POI 写入器不便于产生的单元格（inlineStr、公式缓存值、错误值等）。 */
  private def minimalXlsx(sheetBody: String): Array[Byte] = {
    val parts = Seq(
      "[Content_Types].xml" -> s"""<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>""",
      "_rels/.rels" -> """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""",
      "xl/workbook.xml" -> """<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets>
</workbook>""",
      "xl/_rels/workbook.xml.rels" -> """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>""",
      "xl/worksheets/sheet1.xml" -> sheetBody
    )
    val bos = new ByteArrayOutputStream()
    val zip = new ZipOutputStream(bos)
    parts.foreach { (name, content) =>
      zip.putNextEntry(new ZipEntry(name))
      zip.write(content.getBytes("UTF-8"))
      zip.closeEntry()
    }
    zip.close()
    bos.toByteArray
  }

  test("readRow returns rows sequentially") {
    Using.resource(openReader()) { reader =>
      var count = 0
      var row = reader.readRow()
      while (row.isDefined) {
        count += 1
        row.get should not be empty
        row = reader.readRow()
      }
      count should be(sampleData.size)
    }
  }

  test("forEachRow iterates all rows") {
    Using.resource(openReader()) { reader =>
      var count = 0
      reader.forEachRow(row => {
        count += 1
        row should not be empty
      })
      count should be(sampleData.size)
    }
  }

  test("readRow with types converts values") {
    Using.resource(openReader()) { reader =>
      val row = reader.readRow()
      row shouldBe defined
      val types = row.get.map(_ => DataType.String)
      val typed = reader.readRow(types)
      typed shouldBe defined
      typed.get.length should be(row.get.length)
      typed.get.foreach(_ shouldBe a[String])
    }
  }

  test("readRow converts declared temporal and numeric types") {
    val workbook = new XSSFWorkbook()
    val bytes =
      try {
        val sheet = workbook.createSheet("typed")
        val row = sheet.createRow(0)
        row.createCell(0).setCellValue("2024-01-01")
        row.createCell(1).setCellValue(java.sql.Date.valueOf(LocalDate.of(2024, 1, 1)))
        row.createCell(2).setCellValue("2024-01-01 12:30:00")
        row.createCell(3).setCellValue(3.14)
        row.createCell(4).setCellValue("Y")
        val bos = new ByteArrayOutputStream()
        workbook.write(bos)
        bos.toByteArray
      } finally workbook.close()

    Using.resource(new StreamingReader(new ByteArrayInputStream(bytes))) { reader =>
      val types = Array[DataType](DataType.Date, DataType.Date, DataType.DateTime,
        DataType.Double, DataType.Boolean)
      val row = reader.readRow(types)
      row shouldBe defined
      row.get should contain theSameElementsInOrderAs Seq(
        LocalDate.of(2024, 1, 1),
        LocalDate.of(2024, 1, 1),
        LocalDateTime.of(2024, 1, 1, 12, 30),
        3.14,
        true)
    }
  }

  test("streaming write/read round trip preserves declared types") {
    val values = Seq[Any](
      "张三", 123, 123456789L, 1.5f, 3.14d, true,
      LocalDate.of(2024, 1, 1), LocalDateTime.of(2024, 1, 1, 12, 30),
      LocalTime.of(12, 30, 0), YearMonth.of(2024, 1), MonthDay.of(1, 1),
      Instant.parse("2024-01-01T04:30:00Z"))
    val types = Array[DataType](DataType.String, DataType.Integer, DataType.Long, DataType.Float,
      DataType.Double, DataType.Boolean, DataType.Date, DataType.DateTime, DataType.Time,
      DataType.YearMonth, DataType.MonthDay, DataType.Instant)

    val bytes = Using.resource(new StreamingWriter()) { writer =>
      writer.writeRow(values *)
      val bos = new ByteArrayOutputStream()
      writer.save(bos)
      bos.toByteArray
    }
    Using.resource(new StreamingReader(new ByteArrayInputStream(bytes))) { reader =>
      val row = reader.readRow(types)
      row shouldBe defined
      val got = row.get
      got(0) shouldBe "张三"
      got(1) shouldBe 123
      got(2) shouldBe 123456789L
      got(3) shouldBe 1.5f
      got(4) shouldBe 3.14d
      got(5) shouldBe true
      got(6) shouldBe LocalDate.of(2024, 1, 1)
      got(7) shouldBe LocalDateTime.of(2024, 1, 1, 12, 30)
      got(8) shouldBe LocalTime.of(12, 30)
      got(9) shouldBe YearMonth.of(2024, 1)
      got(10) shouldBe MonthDay.of(1, 1)
      got(11) shouldBe Instant.parse("2024-01-01T04:30:00Z")
    }
  }

  test("reads inline strings, cached formula values and error cells") {
    val bytes = minimalXlsx(
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        |<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
        |<row r="1">
        |<c r="A1" t="inlineStr"><is><t>内联文本</t></is></c>
        |<c r="B1"><f>1+2</f><v>3</v></c>
        |<c r="C1" t="str"><f>CONCATENATE(&quot;a&quot;,&quot;b&quot;)</f><v>ab</v></c>
        |<c r="D1" t="e"><v>#DIV/0!</v></c>
        |<c r="E1" t="b"><v>1</v></c>
        |<c r="F1"><v>2.5</v></c>
        |</row></sheetData></worksheet>""".stripMargin)
    Using.resource(new StreamingReader(new ByteArrayInputStream(bytes))) { reader =>
      val row = reader.readRow()
      row shouldBe defined
      val got = row.get
      got(0) shouldBe "内联文本"
      got(1) shouldBe 3.0
      got(2) shouldBe "ab"
      (got(3) == null) shouldBe true
      got(4) shouldBe true
      got(5) shouldBe 2.5
    }
  }

  test("skipRows skips specified number of rows") {
    Using.resource(openReader()) { reader =>
      var totalRows = 0
      var r = reader.readRow()
      while (r.isDefined) { totalRows += 1; r = reader.readRow() }
      totalRows should be(sampleData.size)
    }

    Using.resource(openReader()) { reader =>
      reader.skipRows = 1
      val first = reader.readRow()
      first shouldBe defined
      first.get should contain theSameElementsInOrderAs sampleData(1)
      reader.currentRowNum should be >= 1
    }
  }

  test("currentRowNum tracks read position") {
    Using.resource(openReader()) { reader =>
      reader.currentRowNum should be(0)
      reader.readRow()
      reader.currentRowNum should be(1)
      reader.readRow()
      reader.currentRowNum should be(2)
    }
  }

  test("comments parsing returns map") {
    Using.resource(new StreamingReader(
      ClassLoaders.getResourceAsStream("sample.xlsx", classOf[StreamingReaderTest]).get, 0)) { reader =>
      val comments = reader.comments
      comments shouldBe a[Map[?, ?]]
      comments.get("A1") shouldBe defined
      comments("A1") should include("jx:each")
    }
  }
}
