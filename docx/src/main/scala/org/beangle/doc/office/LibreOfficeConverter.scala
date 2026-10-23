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

package org.beangle.doc.office

import com.sun.star.beans.PropertyValue
import com.sun.star.frame.{XComponentLoader, XStorable}
import com.sun.star.lang.XComponent
import com.sun.star.uno.{UnoRuntime, XComponentContext}
import org.beangle.commons.bean.{Disposable, Initializing}
import org.beangle.doc.office.LibreOfficeLauncher.Configuration

import java.io.File
import scala.compiletime.uninitialized

/** 利用LibreOffice转换docx到pdf（单进程 UNO 直连）
 */
class LibreOfficeConverter extends Initializing, Disposable {
  /** 0 means pick a free port starting from 2002. */
  var port: Int = 0

  private var launcher: Option[LibreOfficeLauncher] = None
  private var context: XComponentContext = uninitialized

  override def init(): Unit = {
    val cfg = new Configuration
    cfg.port = port
    val l = new LibreOfficeLauncher(cfg)
    context = l.launch()
    launcher = Some(l)
  }

  def convertToPdf(input: File, output: File): Unit = {
    require(null != context, "LibreOfficeConverter is not initialized, call init() first")
    val desktop = UnoRuntime.queryInterface(
      classOf[XComponentLoader],
      context.getServiceManager.createInstanceWithContext("com.sun.star.frame.Desktop", context)
    )
    val hidden = new PropertyValue
    hidden.Name = "Hidden"
    hidden.Value = true

    val doc = desktop.loadComponentFromURL(fileUrl(input), "_blank", 0, Array(hidden))
    if (null == doc) {
      throw new RuntimeException(s"Cannot load document: ${input.getAbsolutePath}")
    }
    try {
      val storable = UnoRuntime.queryInterface(classOf[XStorable], doc)
      val filter = new PropertyValue
      filter.Name = "FilterName"
      filter.Value = pdfFilterName(input.getName)
      val overwrite = new PropertyValue
      overwrite.Name = "Overwrite"
      overwrite.Value = true
      storable.storeToURL(fileUrl(output), Array(filter, overwrite))
    } finally {
      closeQuietly(doc)
    }
  }

  override def destroy(): Unit = {
    launcher.foreach(_.shutdown())
    launcher = None
    context = null
  }

  private def fileUrl(f: File): String = f.getAbsoluteFile.toURI.toString

  private def pdfFilterName(fileName: String): String = {
    val lower = fileName.toLowerCase
    if (lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".odt") ||
      lower.endsWith(".txt") || lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".rtf")) {
      "writer_pdf_Export"
    } else if (lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".ods") ||
      lower.endsWith(".csv")) {
      "calc_pdf_Export"
    } else if (lower.endsWith(".ppt") || lower.endsWith(".pptx") || lower.endsWith(".odp")) {
      "impress_pdf_Export"
    } else {
      "writer_pdf_Export"
    }
  }

  private def closeQuietly(doc: XComponent): Unit = {
    try {
      doc.dispose()
    } catch {
      case _: Throwable =>
    }
  }
}
