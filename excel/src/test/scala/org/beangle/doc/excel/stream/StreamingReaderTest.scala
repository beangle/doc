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
      comments shouldBe a[Map[_, _]]
      comments.get("A1") shouldBe defined
      comments("A1") should include("jx:each")
    }
  }
}
