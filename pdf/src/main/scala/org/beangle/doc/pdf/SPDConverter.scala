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

import com.itextpdf.kernel.pdf.{PdfDocument, PdfReader}
import org.beangle.commons.lang.Strings
import org.beangle.doc.core.{Orientation, PrintOptions}
import org.beangle.doc.pdf.cdt.ChromePdfMaker
import org.beangle.doc.pdf.wk.WKPdfMaker

import java.io.File
import java.net.URI

object SPDConverter {
  def getInstance(): SPDConverter = {
    if (ChromePdfMaker.isAvailable) {
      new SPDConverter(new ChromePdfMaker)
    } else if (WKPdfMaker.isAvailable) {
      new SPDConverter(new WKPdfMaker)
    } else {
      throw new RuntimeException("Cannot find suitable PdfMaker")
    }
  }

  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      println("Usage:SPDConverter some_url or local file")
      return
    }
    val url = args(0)
    val pdf = if args.length > 1 then new File(args(1)) else File.createTempFile("doc", ".pdf")
    val success = SPDConverter.getInstance().print(URI.create(url), pdf, PrintOptions.defaultOptions)
    if (success) println(s"pdf is locate ${pdf.getAbsolutePath}")
  }
}

/** Single Page Document
 * 单页面文档的默认打印
 */
class SPDConverter(pdfMaker: PdfMaker) {

  def convert(uri: URI, pdf: File): Boolean = {
    convert(uri, pdf, PrintOptions.defaultOptions)
  }

  def convert(uri: URI, pdf: File, options: PrintOptions): Boolean = {
    if (uri.getScheme.equalsIgnoreCase("file")) {
      if (!new File(uri).exists()) {
        Logger.error("Cannot find " + uri + ", conversion aborted!")
        return false
      }
    }
    print(uri, pdf, options)
  }

  def close(): Unit = {
    pdfMaker.close()
  }

  private def print(uri: URI, pdf: File, options: PrintOptions): Boolean = {
    var result = pdfMaker.convert(uri, pdf, options)
    if (result) {
      if (options.shrinkTo1Page && getNumberOfPages(pdf) > 1) {
        Logger.debug("enable smart shrinking")
        pdf.delete()
        options.shrinkToFit = false
        result = pdfMaker.convert(uri, pdf, options)
        var scale = 0.95d
        while (getNumberOfPages(pdf) > 1 && scale > 0.5) {
          Logger.debug(s"start zooming at ${scale - 0.05}")
          options.scale = scale
          result = pdfMaker.convert(uri, pdf, options)
          scale -= 0.05
        }
      }
      if (result && options.orientation == Orientation.Landscape) {
        val portrait = new File(pdf.getParent + File.separator + Strings.replace(pdf.getName, ".pdf", ".portrait.pdf"))
        Docs.rotate(pdf, portrait, -90)
        pdf.delete()
        portrait.renameTo(pdf)
      }
      Logger.debug(s"convert pdf ${pdf.getAbsolutePath}")
    }
    result
  }

  private def getNumberOfPages(pdf: File): Int = {
    if (pdf.exists()) {
      val originDoc = new PdfDocument(new PdfReader(pdf))
      val pages = originDoc.getNumberOfPages
      originDoc.close()
      pages
    } else {
      0
    }
  }
}
