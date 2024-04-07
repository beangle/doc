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

package org.beangle.doc.transfer.importer

import org.beangle.commons.io.{DataType, IOs}
import org.beangle.commons.lang.Strings
import org.beangle.commons.logging.Logging
import org.beangle.doc.transfer.Format
import org.beangle.doc.transfer.importer.{Attribute, Reader}

import java.io.LineNumberReader

/**
 * CsvItemReader class.
 *
 * @author chaostone
 */
class CsvReader(reader: LineNumberReader) extends Reader with Logging {

  private var headIndex = 0
  private var indexInCsv = 1

  override def readAttributes(): List[Attribute] = {
    var line: String = null
    while (indexInCsv < headIndex) {
      try {
        line = reader.readLine()
      } catch {
        case e: Throwable => logger.error("read csv", e);
      }
      indexInCsv += 1
    }
    val titles = Strings.split(line, ",")
    val attrList = new collection.mutable.ListBuffer[Attribute]
    titles.indices foreach { i =>
      attrList += Attribute(i + 1, titles(i), DataType.String, titles(i))
    }
    attrList.toList
  }

  def read(): Any = {
    var curData: String = null
    try {
      curData = reader.readLine()
    } catch {
      case e1: Throwable => logger.error(curData, e1);
    }
    indexInCsv += 1
    if (null == curData) {
      null
    } else {
      Strings.split(curData, ",")
    }
  }

  def setIndex(headIndex: Int): Unit = {
    this.headIndex = headIndex
  }

  def format: Format = {
    Format.Csv
  }

  override def close(): Unit = {
    IOs.close(reader)
  }

  /** 当前数据的位置 */
  override def location: String = {
    indexInCsv.toString
  }
}
