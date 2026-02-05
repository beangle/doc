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

import java.io.*

object PdfMerger {
  /** pdf合并
   *
   * @return 合并后的pdf的二进制内容
   * */
  def merge(ins: Seq[InputStream], bos: OutputStream): Unit = {
    // 创建一个新的PDF
    val writer = new PdfWriter(bos)
    val document = new PdfDocument(writer)
    ins foreach { is =>
      val bytes = IOs.readBytes(is)
      if (bytes.length > 0) {
        val reader = new PdfReader(new ByteArrayInputStream(bytes))
        val originDoc = new PdfDocument(reader)
        val pageCount = originDoc.getNumberOfPages
        val pages = originDoc.copyPagesTo(1, pageCount, document).iterator()
        while (pages.hasNext) {
          pages.next().flush()
        }
        originDoc.close()
      }
    }
    document.close()
  }

  def mergeFiles(filePaths: collection.Seq[File], target: File): Unit = {
    val ins = filePaths.flatMap { f =>
      if (f.exists()) {
        if (f.length() == 0) {
          PdfLogger.info(s"ignore empty file ${f.getAbsolutePath}")
          None
        } else if (f.getAbsolutePath.endsWith(".pdf") || f.getAbsolutePath.endsWith(".PDF")) {
          Some(new FileInputStream(f))
        } else {
          PdfLogger.info(s"illegal pdf file ${f.getAbsolutePath}")
          None
        }
      } else None
    }.toSeq
    val part = new File(target.getAbsolutePath + ".part")
    var os: OutputStream = null
    try {
      os = new FileOutputStream(part)
      merge(ins, os)
      os.close()
      if target.exists() then target.delete()
      part.renameTo(target)
    } finally {
      IOs.close(os)
      if (part.exists()) part.delete()
    }
  }
}
