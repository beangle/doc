/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.doc.pdf

import java.io.{File, FileOutputStream}

import com.itextpdf.text.pdf.{PdfReader, PdfWriter}
import com.itextpdf.text.{Document, Image, PageSize}

object Rotation {
  def roate(in: File, out: File,degree:Int): Unit = {
    val pdfReader = new PdfReader(in.toURI.toURL)
    val document = new Document()
    val pdfWriter = PdfWriter.getInstance(document, new FileOutputStream(out))
    pdfWriter.setStrictImageSequence(false)
    document.setPageSize(PageSize.A4)
    document.open()
    val pageCount = pdfReader.getNumberOfPages
    var i = 1
    while (i <= pageCount) {
      document.newPage
      val page = pdfWriter.getImportedPage(pdfReader, i)
      val image = Image.getInstance(page)
      image.setRotationDegrees(degree.toFloat)
      image.setAbsolutePosition(0.toFloat, 0.toFloat)
      document.add(image)
      i += 1
    }
    document.close()
  }

}
