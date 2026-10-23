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

package org.beangle.doc.office

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.io.{File, FileOutputStream}
import java.nio.file.Files
import scala.compiletime.uninitialized

class LibreOfficeConverterTest extends AnyFunSpec, Matchers, BeforeAndAfterAll {

  private val sofficeAvailable = LibreOfficeLauncher.findSoffice().isDefined
  private var converter: LibreOfficeConverter = uninitialized
  private var workDir: File = uninitialized

  override def beforeAll(): Unit = {
    assume(sofficeAvailable, "LibreOffice (soffice) is not installed; skip UNO conversion tests")
    workDir = Files.createTempDirectory("lo-converter-test").toFile
    converter = new LibreOfficeConverter
    converter.init()
  }

  override def afterAll(): Unit = {
    if (null != converter) converter.destroy()
    if (null != workDir) {
      Option(workDir.listFiles()).foreach(_.foreach(_.delete()))
      workDir.delete()
    }
  }

  private def writeDocx(text: String): File = {
    val file = new File(workDir, s"sample-${System.nanoTime()}.docx")
    val doc = new XWPFDocument()
    doc.createParagraph().createRun().setText(text)
    val os = new FileOutputStream(file)
    try doc.write(os)
    finally os.close()
    doc.close()
    file
  }

  private def writeHtml(text: String): File = {
    val file = new File(workDir, s"sample-${System.nanoTime()}.html")
    Files.writeString(file.toPath, s"<html><body><p>$text</p></body></html>")
    file
  }

  private def pdfOutput(input: File): File =
    new File(workDir, input.getName.replaceAll("\\.[^.]+$", "") + ".pdf")

  describe("LibreOfficeConverter") {
    it("converts docx to a non-empty pdf") {
      val input = writeDocx("hello docx to pdf")
      val output = pdfOutput(input)
      converter.convertToPdf(input, output)
      output.exists() shouldBe true
      output.length() should be > 0L
      // PDF magic
      val head = Files.readAllBytes(output.toPath).take(4)
      head shouldBe Array('%', 'P', 'D', 'F').map(_.toByte)
    }

    it("converts html to a non-empty pdf") {
      val input = writeHtml("hello html to pdf")
      val output = pdfOutput(input)
      converter.convertToPdf(input, output)
      output.exists() shouldBe true
      output.length() should be > 0L
      val head = Files.readAllBytes(output.toPath).take(4)
      head shouldBe Array('%', 'P', 'D', 'F').map(_.toByte)
    }

    it("overwrites an existing pdf") {
      val input = writeDocx("overwrite target")
      val output = pdfOutput(input)
      Files.writeString(output.toPath, "stale")
      converter.convertToPdf(input, output)
      output.length() should be > 4L
      val head = Files.readAllBytes(output.toPath).take(4)
      head shouldBe Array('%', 'P', 'D', 'F').map(_.toByte)
    }

    it("fails when converting a missing input file") {
      val input = new File(workDir, "does-not-exist.docx")
      val output = pdfOutput(input)
      an[Exception] should be thrownBy converter.convertToPdf(input, output)
    }
  }

  describe("LibreOfficeLauncher") {
    it("findSoffice returns a path when LibreOffice is installed") {
      val path = LibreOfficeLauncher.findSoffice()
      path shouldBe defined
      path.get.toFile.exists() shouldBe true
    }
  }
}
