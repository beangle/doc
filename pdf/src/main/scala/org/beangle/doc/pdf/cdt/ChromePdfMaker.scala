package org.beangle.doc.pdf.cdt

import org.beangle.commons.codec.binary.Base64
import org.beangle.commons.collection.Collections
import org.beangle.commons.logging.Logging
import org.beangle.doc.core.{Orientation, PrintOptions}
import org.beangle.doc.pdf.PdfMaker

import java.io.File
import java.net.URI

class ChromePdfMaker extends PdfMaker, Logging {

  def convert(uri: URI, pdf: File, options: PrintOptions): Boolean = {
    val chrome = Chrome.start(true)
    try {
      val params = Collections.newMap[String, Any]
      params.put("paperWidth", options.pageSize.width.inches)
      params.put("paperHeight", options.pageSize.height.inches)

      params.put("landscape", options.orientation == Orientation.Landscape)
      params.put("printBackground", options.printBackground)

      val m = options.margin
      params.put("marginTop", m.top.inches)
      params.put("marginBottom", m.bottom.inches)
      params.put("marginLeft", m.left.inches)
      params.put("marginRight", m.right.inches)

      if options.scale < 1.0 then params.put("scale", options.scale)
      params.put("displayHeaderFooter", options.printHeaderFooter)
      params.put("preferCSSPageSize", false)
      params.put("transferMode", "ReturnAsBase64")
      val page = chrome.open(uri.toString)
      val data = page.printToPDF(params.toMap)
      chrome.close(page)
      Base64.dump(data, pdf)
      true
    } catch {
      case e: Throwable =>
        logger.error("Convert error", e)
        false
    } finally {
      chrome.exit()
    }
  }

  def close(): Unit = {

  }
}
