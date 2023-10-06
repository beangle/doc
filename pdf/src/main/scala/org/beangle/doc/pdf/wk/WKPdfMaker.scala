package org.beangle.doc.pdf.wk

import org.beangle.commons.logging.Logging
import org.beangle.doc.core.{ErrorPolicy, PrintOptions}
import org.beangle.doc.pdf.PdfMaker

import java.io.File
import java.net.URI

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
