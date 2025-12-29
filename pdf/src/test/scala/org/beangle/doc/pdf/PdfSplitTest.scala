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

package org.beangle.doc.pdf

import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.{PdfDocument, PdfReader, PdfWriter}
import com.itextpdf.layout.Canvas
import com.itextpdf.layout.element.Image

import java.io.{ByteArrayInputStream, FileInputStream, FileOutputStream}
import scala.util.Using
import scala.util.matching.Regex

class PdfSplitTest {

  def main(args: Array[String]): Unit = {
    val n = new FileInputStream("D:\\tmp\\成绩单.pdf")
    var i: Int = 0
    val nameRegex: Regex = """学生姓名:(.+?)(?=\s|，|。|、|\n|$)""".r
    val codeRegex: Regex = """学号:(.+?)(?=\s|，|。|、|\n|$)""".r

    PdfSpliter.split(n, save = bytes => {
      i += 1
      val doc = new PdfDocument(new PdfReader(new ByteArrayInputStream(bytes)))
      val pageText = PdfTextExtractor.getTextFromPage(doc.getPage(1))
      val nameMatches = nameRegex.findAllMatchIn(pageText)
      var code: String = ""
      var name: String = ""
      nameMatches.foreach { m =>
        val n = m.group(1).trim // 去除前后空格
        if n.nonEmpty then name = n
      }
      val codeMatches = codeRegex.findAllMatchIn(pageText)
      codeMatches.foreach { m =>
        val c = m.group(1).trim // 去除前后空格
        if c.nonEmpty then code = c
      }
      doc.close()
      var fileName: String = null
      fileName = "D:\\tmp\\parts\\" + code + " " + name + ".pdf"
      Using(new FileOutputStream(fileName)) { f =>
        f.write(bytes)
      }

      val newDoc = new PdfDocument(new PdfReader(new ByteArrayInputStream(bytes)), new PdfWriter(new FileOutputStream(fileName)))
      val firstPage = newDoc.getPage(1)
      val width = firstPage.getPageSize.getWidth
      val height = firstPage.getPageSize.getHeight
      val stampX = width - 260 - 20
      val stampY = 25f
      val imageData = ImageDataFactory.create("d:\\signature.png")
      val stampImage = new Image(imageData)
        .setWidth(108f)
        .setHeight(108f)

      val pdfCanvas = new PdfCanvas(firstPage)
      val canvas = new Canvas(pdfCanvas, firstPage.getPageSize)
      stampImage.setFixedPosition(stampX, stampY)
      canvas.add(stampImage)
      canvas.close()
      pdfCanvas.release()
      newDoc.close()
    })
  }
}
