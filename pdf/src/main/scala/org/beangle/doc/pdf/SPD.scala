/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.doc.pdf

import com.itextpdf.text.pdf.PdfReader
import org.beangle.commons.lang.Strings
import org.beangle.commons.logging.Logging
import org.beangle.doc.core.{ErrorPolicies, PageSizes}
import org.beangle.doc.pdf.wk.{Htmltopdf, WKPage}

import java.io.File
import java.net.URL

/** Single Page Document
 * 单页面文档的默认打印
 */
object SPD extends Logging {

  def convertURL(url: URL, pdf: File, settings: Map[String, String] = Map.empty): Boolean = {
    printToOnePage(url.toString, pdf, settings)
  }

  def convertFile(html: File, pdf: File, settings: Map[String, String] = Map.empty): Boolean = {
    if (!html.exists()) {
      logger.error("Cannot find " + html + ", conversion aborted!")
      return false
    }
    printToOnePage(html.toString, pdf, settings)
  }

  private def printToOnePage(html: String, pdf: File, settings: Map[String, String]): Boolean = {
    var result = convert(html, pdf, settings)
    if (getNumberOfPages(pdf) > 1) {
      pdf.delete()
      logger.info("enable smart shrinking")
      result = convert(html, pdf, settings + ("disable-smart-shrinking" -> "false"))
    }
    result
  }

  private def getNumberOfPages(pdf: File): Int = {
    val pdfReader = new PdfReader(pdf.toURI.toURL)
    val pages = pdfReader.getNumberOfPages
    pdfReader.close()
    pages
  }

  private def convert(html: String, pdf: File, settings: Map[String, String]): Boolean = {
    if (pdf.exists() && !pdf.canWrite) {
      logger.error("Cannot write target pdf " + pdf + ", conversion aborted!")
      return false
    }

    val htmltopdf = Htmltopdf.create().pageSize(PageSizes.A4)
      .compression(true)
      .margin("0in", "0in", "0in", "0in") //让应用程序设定边距

    if (System.getProperty("os.name").toLowerCase.contains("windows")) {
      htmltopdf.dpi(200) //较低的dpi会使得字挤在一起
    }

    val page = WKPage.url(html)
      .defaultEncoding("utf8")
      .produceForms(true)
      .usePrintMediaType(true)
      .loadImages(true).handleErrors(ErrorPolicies.Abort)

    if ("true" == settings.getOrElse("disable-smart-shrinking", "true")) {
      htmltopdf.disableSmartShrinking(true)
      page.enableIntelligentShrinking(false)
    } else {
      htmltopdf.disableSmartShrinking(false)
      page.enableIntelligentShrinking(true)
    }

    htmltopdf.page(page)

    settings.foreach { case (k, v) =>
      htmltopdf.set(k, v)
    }
    htmltopdf.error(logger.error(_))
    val isLandscape = htmltopdf.getSetting("orientation").getOrElse("-") == "Landscape"
    if (isLandscape) {
      val portrait = new File(pdf.getParent + File.separator + Strings.replace(pdf.getName, ".pdf", ".portrait.pdf"))
      val success = htmltopdf.saveAs(portrait)
      if (success) {
        Rotation.roate(portrait, pdf, -90)
      }
      true
    } else {
      htmltopdf.saveAs(pdf)
    }
  }
}
