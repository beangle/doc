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

import org.beangle.commons.io.Dirs
import org.beangle.commons.io.Files./
import org.beangle.commons.lang.Strings

import java.io.File

object ConvertTest {

  def main(args: Array[String]): Unit = {
    val srcDir = "D:\\tmp\\2016"
    val converter = new LibreOfficeConverter()
    converter.init()
    Dirs.on(srcDir).ls() foreach { n =>
      val file = new File(srcDir + / + n)
      if (file.isFile && (n.endsWith(".doc") || n.endsWith(".docx"))) {
        val outfile = Strings.substringBeforeLast(n, ".").toUpperCase() + ".pdf"
        println(s"converting to ${outfile}")
        converter.convertToPdf(file, new File(srcDir + / + outfile))
      }
    }
    converter.destroy()
  }

}
