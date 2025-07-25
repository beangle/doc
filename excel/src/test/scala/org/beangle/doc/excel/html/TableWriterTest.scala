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

package org.beangle.doc.excel.html

import org.beangle.commons.io.{Files, IOs}
import org.beangle.commons.lang.ClassLoaders

import java.io.{File, FileOutputStream}

object TableWriterTest {

  def main(args: Array[String]): Unit = {
    val file = File.createTempFile("template", ".xlsx")
    val t = IOs.readString(ClassLoaders.getResourceAsStream("table.html").head)
    val os = new FileOutputStream(file)
    val workbook = TableWriter.write(t)
    workbook.write(os)
    println(file.getAbsolutePath)
    os.close()
  }

}
