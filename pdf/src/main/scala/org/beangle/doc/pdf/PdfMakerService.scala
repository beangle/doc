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
import org.beangle.commons.bean.{Disposable, Initializing}
import org.beangle.commons.concurrent.Timers
import org.beangle.commons.lang.Strings
import org.beangle.doc.core.{Orientation, PrintOptions}
import org.beangle.doc.pdf.cdt.ChromePdfMaker
import org.beangle.doc.pdf.wk.WKPdfMaker

import java.io.File
import java.net.URI
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

object PdfMakerService {

  /** Whether Chrome or wkhtmltopdf is available on this host. */
  def isAvailable: Boolean = ChromePdfMaker.isAvailable || WKPdfMaker.isAvailable
}

/** Container-managed PDF service.
 *
 * Creates PdfMaker in `init()`, shares it across concurrent prints, and calls `maker.close()` after
 * `idleTimeout` when no tasks are in flight. ChromePdfMaker recreates Chrome on the next convert.
 * Register `destroy()` as the bean teardown hook.
 */
class PdfMakerService extends Initializing, Disposable {

  /** Max Chrome tabs when ChromePdfMaker is selected; should be >= expected parallelism. */
  var maxPages: Int = 10

  /** Idle time before releasing PdfMaker; reset after each print completes. */
  var idleTimeout: Duration = Duration.ofMinutes(5)

  /** Number of print tasks currently running; idle release runs only when this is 0. */
  private val inFlight = new AtomicInteger(0)

  private var maker: PdfMaker = _

  def print(uri: URI, pdf: File): Boolean = {
    print(uri, pdf, PrintOptions.defaultOptions)
  }

  def print(uri: URI, pdf: File, options: PrintOptions): Boolean = {
    if (uri.getScheme.equalsIgnoreCase("file")) {
      if (!new File(uri).exists()) {
        Logger.error("Cannot find " + uri + ", conversion aborted!")
        return false
      }
    }
    inFlight.incrementAndGet()
    try {
      doPrint(this.maker, uri, pdf, options)
    } finally {
      inFlight.decrementAndGet()
      // Schedule an idle release; overlapping timers are safe because each checks inFlight == 0.
      scheduleIdleRelease()
    }
  }

  /** Immediately releases PdfMaker; called when the container destroys this bean. */
  override def destroy(): Unit = {
    releaseMaker()
  }

  override def init(): Unit = {
    // Prefer headless Chrome for JS-heavy pages; fall back to wkhtmltopdf.
    if (ChromePdfMaker.isAvailable) {
      val chrome = new ChromePdfMaker
      chrome.maxPages = maxPages
      this.maker = chrome
    } else if (WKPdfMaker.isAvailable) {
      this.maker = new WKPdfMaker
    } else {
      throw new RuntimeException("Cannot find suitable PdfMaker")
    }
  }

  private def scheduleIdleRelease(): Unit = {
    Timers.setTimeout(idleTimeoutSeconds, () => if (inFlight.get() == 0) releaseMaker())
  }

  /** Release underlying resources (e.g. Chrome process); keeps the PdfMaker instance for reuse. */
  private def releaseMaker(): Unit = {
    if (null != maker) {
      try maker.close()
      catch
        case e: Throwable => Logger.error("Close PdfMaker error", e)
    }
  }

  private def idleTimeoutSeconds: Int = {
    Math.max(1, (idleTimeout.toMillis + 999) / 1000).toInt
  }

  /** Post-process conversion result: shrink-to-one-page and landscape rotation (same as SPDConverter). */
  private def doPrint(maker: PdfMaker, uri: URI, pdf: File, options: PrintOptions): Boolean = {
    var result = maker.convert(uri, pdf, options)
    if (result) {
      if (options.shrinkTo1Page && getNumberOfPages(pdf) > 1) {
        Logger.debug("enable smart shrinking")
        pdf.delete()
        options.shrinkToFit = false
        result = maker.convert(uri, pdf, options)
        var scale = 0.95d
        while (getNumberOfPages(pdf) > 1 && scale > 0.5) {
          Logger.debug(s"start zooming at ${scale - 0.05}")
          options.scale = scale
          result = maker.convert(uri, pdf, options)
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
