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

package org.beangle.doc.transfer.exporter

import org.beangle.commons.csv.{CsvFormat, CsvWriter as CSVWriter}
import org.beangle.doc.transfer.Format

import java.io.{OutputStream, OutputStreamWriter}

class CsvWriter(val os: OutputStream) extends Writer {
  val csvw = new CSVWriter(new OutputStreamWriter(os, "utf-8"),
    new CsvFormat.Builder().escape(CSVWriter.NoEscapeChar).build())

  override def write(obj: Any): Unit = {
    csvw.write(obj.asInstanceOf[Array[Any]])
  }

  override def writeHeader(caption: Option[String], titles: Array[String]): Unit = {
    write(titles)
  }

  override def format: Format = Format.Csv

  override def close(): Unit = csvw.close()
}
