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

import com.itextpdf.text.pdf.PdfReader
import org.beangle.commons.lang.Strings
import org.beangle.commons.logging.Logging
import org.beangle.doc.core.{Orientation, PrintOptions}
import org.beangle.doc.pdf.cdt.ChromePdfMaker
import org.beangle.doc.pdf.wk.WKPdfMaker

import java.io.File
import java.net.URI

object SPDConverter {
  def getInstance(): PdfMaker = {
    if (ChromePdfMaker.isAvailable()) {
      new ChromePdfMaker
    } else if (WKPdfMaker.isAvailable()) {
      new WKPdfMaker
    } else {
      throw new RuntimeException("Cannot find suitable PdfMaker")
    }
  }
}

/** Single Page Document
 * 单页面文档的默认打印
 */
class SPDConverter(pdfMaker: PdfMaker) extends Logging {

  def convert(uri: URI, pdf: File, options: PrintOptions): Boolean = {
    if (uri.getScheme.equalsIgnoreCase("file")) {
      if (!new File(uri).exists()) {
        logger.error("Cannot find " + uri + ", conversion aborted!")
        return false
      }
    }
    printToOnePage(uri, pdf, options)
  }

  def close(): Unit = {
    pdfMaker.close()
  }

  private def printToOnePage(uri: URI, pdf: File, options: PrintOptions): Boolean = {
    var result = pdfMaker.convert(uri, pdf, options)
    if (options.shrinkTo1Page && getNumberOfPages(pdf) > 1) {
      logger.debug("enable smart shrinking")
      pdf.delete()
      options.shrinkToFit = false
      result = pdfMaker.convert(uri, pdf, options)
      var scale = 0.95d
      while (getNumberOfPages(pdf) > 1 && scale > 0.5) {
        logger.debug(s"start zooming at ${scale - 0.05}")
        options.scale = scale
        result = pdfMaker.convert(uri, pdf, options)
        scale -= 0.05
      }
    }
    if (result && options.orientation == Orientation.Landscape) {
      val portrait = new File(pdf.getParent + File.separator + Strings.replace(pdf.getName, ".pdf", ".portrait.pdf"))
      Rotation.roate(portrait, pdf, -90)
      pdf.delete()
      portrait.renameTo(pdf)
    }
    logger.debug(s"convert pdf ${pdf.getAbsolutePath}")
    result
  }

  private def getNumberOfPages(pdf: File): Int = {
    if (pdf.exists()) {
      val pdfReader = new PdfReader(pdf.toURI.toURL)
      val pages = pdfReader.getNumberOfPages
      pdfReader.close()
      pages
    } else {
      0
    }
  }
}
