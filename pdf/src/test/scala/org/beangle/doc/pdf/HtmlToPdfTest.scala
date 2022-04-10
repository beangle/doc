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

import java.io.File

import com.itextpdf.text.pdf.PdfWriter

object HtmlToPdfTest {

  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      println("Usage:HtmlToPdfTest file.html")
      return
    }
    val url = args(0)
    val out = new File(url).getParent + File.separator + "temp.pdf"

    SPD.convertFile(new File(url), new File(out),Map("orientation"->"Landscape"))
    //Encryptor.encrypt(new File(out),Some("123"),"456",PdfWriter.ALLOW_PRINTING)
    println("convert " + url + " to " + out)
  }
}
