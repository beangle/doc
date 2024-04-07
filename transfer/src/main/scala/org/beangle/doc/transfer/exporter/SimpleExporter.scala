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

import org.beangle.commons.logging.Logging
import org.beangle.doc.transfer.Format

import java.io.OutputStream

class SimpleExporter extends Exporter with Logging {
  var current: Any = _
  var context: ExportContext = _
  var attrs: Array[String] = _
  var writer: Writer = _

  override def exportData(os: OutputStream, context: ExportContext): Unit = {
    this.context = context
    if null == this.writer then this.writer = buildWriter(os, context)
    var index = -1
    var iter: Iterator[Any] = null
    val items = context.getItems()
    if (null != items) {
      iter = items.iterator
    }
    if (null != iter && !beforeExport()) return
    while (iter.hasNext) {
      index += 1
      current = iter.next()
      exportItem()
    }
    writer.close()
  }

  def buildWriter(os: OutputStream, context: ExportContext): Writer = {
    val format = context.format
    if format == Format.Xlsx then new ExcelWriter(os)
    else if format == Format.Csv then new CsvWriter(os)
    else throw new RuntimeException("Cannot export to other formats, csv/xlsx supported only!")
  }

  def beforeExport(): Boolean = {
    this.attrs = context.attrs
    writer.writeHeader(context.caption, context.titles)
    true
  }

  def exportItem(): Unit = {
    if (null == attrs || attrs.length == 0) {
      if (null != current) writer.write(current)
    } else {
      val values = new Array[Any](attrs.length)
      values.indices foreach { i =>
        try {
          values(i) = context.getPropertyValue(current.asInstanceOf[AnyRef], attrs(i))
        } catch {
          case e: Exception => logger.error("occur in get property :" + attrs(i), e)
        }
      }
      writer.write(values)
    }
  }
}
