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

import org.beangle.commons.lang.time.Stopwatch

import java.io.File

object ConvertTest {

  def main(args: Array[String]): Unit = {
    val inputFile = new File("C:\\Users\\duantihua\\Desktop\\2023年度在线辅修系统功能开发合同.docx")
    val outputFile = new File("C:\\Users\\duantihua\\Desktop\\test.pdf")
    if (inputFile.exists()) {
      val watch = new Stopwatch(true)
      val converter = new LibreOfficeConverter()
      converter.init()
      converter.convertToPdf(inputFile, outputFile)
      converter.destroy()
      println(s"convert using ${watch}")
    }
  }
}
