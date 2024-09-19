package org.beangle.doc.pdf

import com.itextpdf.kernel.pdf.{PdfDocument, PdfReader, PdfWriter}
import org.beangle.commons.io.IOs
import org.beangle.commons.logging.Logging

import java.io.*

object PdfMerger extends Logging {
  /** pdf合并
   *
   * @param inputStreams 要合并的pdf的InputStream数组
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
          logger.info(s"ignore empty file ${f.getAbsolutePath}")
          None
        } else if (f.getAbsolutePath.endsWith(".pdf") || f.getAbsolutePath.endsWith(".PDF")) {
          Some(new FileInputStream(f))
        } else {
          logger.info(s"illegal pdf file ${f.getAbsolutePath}")
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
