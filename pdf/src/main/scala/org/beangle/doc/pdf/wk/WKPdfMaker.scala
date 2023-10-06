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

package org.beangle.doc.pdf.wk

import org.beangle.commons.logging.Logging
import org.beangle.doc.core.util.NativeLoader
import org.beangle.doc.core.{ErrorPolicy, PrintOptions}
import org.beangle.doc.pdf.PdfMaker

import java.io.File
import java.net.URI

object WKPdfMaker {
  def isAvailable(): Boolean = {
    try {
      new NativeLoader("wkhtmltopdf", "libwkhtmltox")
        .find("C:\\Program Files\\wkhtmltopdf\\bin", "latest", classOf[WKLibrary])
      true
    } catch
      case e: Throwable => false
  }
}

class WKPdfMaker extends PdfMaker, Logging {

  def convert(uri: URI, pdf: File, options: PrintOptions): Boolean = {
    if (pdf.exists() && !pdf.canWrite) {
      logger.error("Cannot write target pdf " + pdf + ", conversion aborted!")
      return false
    }

    val htmltopdf = Htmltopdf.create().pageSize(options.pageSize)
      .compression(true)
      .orientation(options.orientation)
      .margin(options.margin) //让应用程序设定边距

    if (System.getProperty("os.name").toLowerCase.contains("windows")) {
      htmltopdf.dpi(200) //较低的dpi会使得字挤在一起
    }

    val page = WKPage.url(uri.toString)
      .defaultEncoding("utf8")
      .produceForms(true)
      .usePrintMediaType(true)
      .showBackground(options.printBackground)
      .loadImages(true).handleErrors(ErrorPolicy.Ignore)

    if (!options.printHeaderFooter) {
      page.header(null, null, null)
    }
    if (options.shrinkToFit) {
      htmltopdf.disableSmartShrinking(false)
      page.enableIntelligentShrinking(true)
    } else {
      htmltopdf.disableSmartShrinking(true)
      page.enableIntelligentShrinking(false)
    }

    htmltopdf.page(page)

    if (java.lang.Double.compare(options.scale, 1.0d) != 0) {
      page.set(ObjectSettings.ZoomFactor, String.valueOf(options.scale))
    }
    htmltopdf.error(logger.error(_))
    htmltopdf.saveAs(pdf)
  }

  def close(): Unit = {
  }
}
