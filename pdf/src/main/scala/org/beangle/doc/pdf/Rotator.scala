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

import com.itextpdf.kernel.pdf.{PdfDocument, PdfReader, PdfWriter}

import java.io.File

object Rotator {
  def rotate(in: File, out: File, degree: Int): Unit = {
    val originDoc = new PdfDocument(new PdfReader(in))
    val writer = new PdfWriter(out)
    val newDoc = new PdfDocument(writer)
    val pageCount = originDoc.getNumberOfPages
    originDoc.copyPagesTo(1, pageCount, newDoc)
    var i = 1
    while (i <= pageCount) {
      val page = newDoc.getPage(i)
      page.setRotation(degree)
      i += 1
    }
    originDoc.close()
    newDoc.close()
    writer.close()
  }

}
