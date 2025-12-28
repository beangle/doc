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
