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
import org.beangle.commons.io.IOs
import org.beangle.commons.logging.Logging

import java.io.*

/** 将单个PDF拆分成每页一个的PDF
 */
object PdfSpliter extends Logging {

  def split(input: InputStream, save: Array[Byte] => Unit): Unit = {
    val srcDoc = new PdfDocument(new PdfReader(input))
    try {
      val totalPages = srcDoc.getNumberOfPages
      for (pageNum <- 1 to totalPages) {
        val bos = new ByteArrayOutputStream()
        val writer = new PdfWriter(bos)
        val targetDoc = new PdfDocument(writer)
        srcDoc.copyPagesTo(pageNum, pageNum, targetDoc)
        targetDoc.close()
        save(bos.toByteArray)
      }
    } finally {
      IOs.close(srcDoc)
    }
  }

}
