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

class ChromePdfMaker extends PdfMaker {

  def convert(uri: URI, pdf: File, options: PrintOptions): Boolean = {
    val chrome = Chrome.start()
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

      options.pageRanges foreach { ranges =>
        params.put("pageRanges", ranges)
      }
      val page = chrome.open(uri.toString)
      val res = page.printToPDF(params.toMap)
      chrome.close(page)
      if (Strings.isEmpty(res._2)) {
        Base64.dump(res._1, pdf)
        true
      } else {
        Logger.error(res._2)
        false
      }
    } catch {
      case e: Throwable =>
        Logger.error("Convert error", e)
        false
    } finally {
      chrome.exit()
    }
  }

  def close(): Unit = {}
}
