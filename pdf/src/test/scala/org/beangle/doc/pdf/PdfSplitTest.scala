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

import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import com.itextpdf.kernel.pdf.{PdfDocument, PdfReader}
import org.beangle.commons.io.Dirs
import org.beangle.commons.io.Files./
import org.beangle.commons.lang.Strings

import java.io.{ByteArrayInputStream, File, FileInputStream, FileOutputStream}
import scala.util.Using
import scala.util.matching.Regex

object PdfSplitTest {

  def split(file: File, targetDir: File): Unit = {
    targetDir.mkdirs()
    println("split file " + file.getAbsolutePath)
    val n = file
    var i: Int = 0
    val nameRegex: Regex = """学生姓名:(.+?)(?=，|。|、|\n|学号|$)""".r
    val codeRegex: Regex = """学号:(.+?)(?=\s|，|。|、|\n|$)""".r

    Docs.split(n) { bytes =>
      i += 1
      val doc = new PdfDocument(new PdfReader(new ByteArrayInputStream(bytes)))
      val pageText = PdfTextExtractor.getTextFromPage(doc.getPage(1), new SimpleTextExtractionStrategy)

      val nameMatches = nameRegex.findAllMatchIn(pageText)
      var code: String = ""
      var name: String = ""
      nameMatches.foreach { m =>
        val n = m.group(1).trim // 去除前后空格
        if n.nonEmpty then name = n
      }
      name = Strings.replace(name, " ", "")
      val codeMatches = codeRegex.findAllMatchIn(pageText)
      codeMatches.foreach { m =>
        val c = m.group(1).trim // 去除前后空格
        if c.nonEmpty then code = c
      }
      doc.close()
      var fileName: String = null
      fileName = targetDir.getAbsolutePath + / + code + " " + name + ".pdf"
      println(s"writing ${fileName}")
      Using(new FileOutputStream(fileName)) { f =>
        f.write(bytes)
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val srcDir = "D:\\tmp\\历年成绩单"
    val targetDir = new File("D:\\tmp" + / + "transcripts")

    Dirs.on(srcDir).ls() foreach { n =>
      val file = new File(srcDir + / + n)
      if (n.startsWith("2015") && file.isFile) {
        split(file, targetDir)
      }
    }
  }

}
