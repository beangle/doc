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

import org.beangle.doc.core.PrintOptions
import org.beangle.doc.pdf.cdt.ChromePdfMaker
import org.beangle.doc.pdf.wk.WKPdfMaker

import java.io.File
import java.net.URI

trait PdfMaker {
  def convert(uri: URI, pdf: File, options: PrintOptions): Boolean

  def close(): Unit
}

object PdfMaker {

  def newMaker(): PdfMaker = {
    if (ChromePdfMaker.isAvailable) {
      new ChromePdfMaker
    } else if (WKPdfMaker.isAvailable) {
      new WKPdfMaker
    } else {
      throw new RuntimeException("Cannot find suitable PdfMaker")
    }
  }
}
