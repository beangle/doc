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

package org.beangle.doc.pdf.cdt

import org.beangle.commons.codec.binary.Base64
import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.Strings
import org.beangle.doc.core.{Orientation, PrintOptions}
import org.beangle.doc.pdf.{Logger, PdfMaker}

import java.io.File
import java.net.URI

object ChromePdfMaker {
  def isAvailable: Boolean = {
    ChromeLauncher.findChrome().nonEmpty
  }
}

/** PdfMaker backed by headless Chrome via CDP. */
class ChromePdfMaker extends PdfMaker {

  /** Lazily started; recreated after explicit shutdown or a dead process. */
  private var chrome: Chrome = _

  /** Max idle tabs kept after convert; excess returned tabs are closed. */
  var maxIdles: Int = 2

  def convert(uri: URI, pdf: File, options: PrintOptions): Boolean = {
    try
      doConvert(uri, pdf, options)
    catch
      case e: Throwable =>
        Logger.error("Convert error", e)
        false
  }

  def close(): Unit = {
    shutdownChrome()
  }

  private def doConvert(uri: URI, pdf: File, options: PrintOptions): Boolean = {
    var page: ChromePage = null
    var c: Chrome = null
    try {
      val params = buildPrintParams(options)
      c = getChrome()
      page = c.open(uri.toString)
      val res = page.printToPDF(params, options.renderDelay)
      if (Strings.isEmpty(res._2)) {
        Base64.dump(res._1, pdf)
        true
      } else {
        Logger.error(res._2)
        false
      }
    } finally {
      if null != c then c.close(page)
    }
  }

  private def getChrome(): Chrome = {
    val c = chrome
    if (null == c || !c.isAlive) synchronized {
      if (null == chrome || !chrome.isAlive) {
        shutdownChrome()
        require(maxIdles > 0)
        chrome = ChromeLauncher.start(maxIdles)
      }
      chrome
    } else c
  }

  private def shutdownChrome(): Unit = {
    if (null != chrome) {
      try chrome.exit()
      catch
        case e: Throwable => Logger.error("Shutdown chrome error", e)
      chrome = null
    }
  }

  private def buildPrintParams(options: PrintOptions): Map[String, Any] = {
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
    options.pageRanges foreach { ranges =>
      params.put("pageRanges", ranges)
    }
    params.toMap
  }
}
