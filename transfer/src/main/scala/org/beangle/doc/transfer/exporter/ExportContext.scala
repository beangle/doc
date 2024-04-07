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

import org.beangle.commons.bean.{DefaultPropertyExtractor, PropertyExtractor}
import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.text.{Formatter, Formatters}
import org.beangle.commons.lang.{Options, Strings}
import org.beangle.doc.transfer.Format

import java.io.OutputStream
import java.net.URL
import scala.collection.mutable

object ExportContext {

  def csv(props: Seq[String]): ExportContext = {
    val ctx = new ExportContext(Format.Csv)
    ctx.header(None, props)
    ctx.convertToString = true
    ctx.exporter = new SimpleExporter()
    ctx
  }

  def excel(caption: Option[String], props: Seq[String]): ExportContext = {
    val ctx = new ExportContext(Format.Xlsx)
    ctx.header(caption, props)
    ctx.exporter = new SimpleExporter()
    ctx
  }

  def template(template: URL): ExportContext = {
    val ctx = new ExportContext(Format.Xlsx)
    ctx.exporter = new ExcelTemplateExporter()
    ctx.template = template
    ctx.convertToString = false
    ctx
  }

  def writeExcel(os: OutputStream, caption: Option[String], titles: Seq[String], items: Iterable[Any]): Unit = {
    val ctx = ExportContext.excel(caption, titles)
    ctx.setItems(items)
    ctx.writeTo(os)
  }

}

class ExportContext(val format: Format) {

  var exporter: Exporter = _

  val datas: collection.mutable.Map[String, Any] = Collections.newMap[String, Any]

  var extractor: PropertyExtractor = new DefaultPropertyExtractor
  /** Convert all property to string before export */
  var convertToString: Boolean = false

  var typeFormatters: Map[Class[_], Formatter] = Map.empty

  var propertyFormatters: Map[String, Formatter] = Map.empty

  val sharedValues = Collections.newMap[String, String]

  var caption: Option[String] = None

  var attrs: Array[String] = _

  var titles: Array[String] = _

  var template: URL = _

  var fileName: String = _

  def registerFormatter(clazz: Class[_], formatter: Formatter): ExportContext = {
    typeFormatters += (clazz -> formatter)
    this
  }

  def registerFormatter(propertyName: String, formatter: Formatter): ExportContext = {
    propertyFormatters += (propertyName -> formatter)
    this
  }

  def getFormatter(propertyName: String, obj: Any): Option[Formatter] = {
    propertyFormatters.get(propertyName) match {
      case None => if (null == obj) None else typeFormatters.get(obj.getClass)
      case p@Some(f) => p
    }
  }

  def buildFileName(suggest: Option[String]): String = {
    val ext = "." + Strings.uncapitalize(this.format.toString)
    this.fileName = suggest match {
      case Some(f) => if (!f.endsWith(ext)) f + ext else f
      case None => "exportFile" + ext
    }
    this.fileName
  }

  def header(caption: Option[String], props: Seq[String]): this.type = {
    this.caption = caption
    val keys = new mutable.ArrayBuffer[String](props.length)
    val titles = new mutable.ArrayBuffer[String](props.length)
    for (prop <- props) {
      //case 1: title
      //case 2: property_path:title
      //case 3: blank.xxx:title:default_value
      if (prop.contains(":")) {
        val key = Strings.substringBefore(prop, ":")
        var sharedValue = ""
        var title = Strings.substringAfter(prop, ":")
        if (key.startsWith("blank.")) {
          if (title.contains(":")) {
            sharedValue = Strings.substringAfter(title, ":")
            title = Strings.substringBefore(title, ":")
          }
          sharedValues.put(key, sharedValue)
        }
        keys += key
        titles += title
      } else {
        titles += prop
      }
    }
    if keys.nonEmpty then this.attrs = keys.toArray
    this.titles = titles.toArray
    this
  }

  def get[T](key: String, clazz: Class[T]): Option[T] = {
    datas.get(key).asInstanceOf[Option[T]]
  }

  def put(key: String, v: Any): this.type = {
    datas.put(key, v)
    this
  }

  def setItems(v: Iterable[Any]): this.type = {
    datas.put("items", v)
    this
  }

  def getItems(): Iterable[Any] = {
    datas.getOrElse("items", List.empty).asInstanceOf[Iterable[Any]]
  }

  def getPropertyValue(target: Object, property: String): Any = {
    sharedValues.get(property) match
      case Some(v) => v
      case None =>
        val value = Options.unwrap(extractor.get(target, property))
        if value == null then ""
        else
          getFormatter(property, value) match {
            case None => if convertToString then Formatters.getDefault(value.getClass).format(value) else value
            case Some(formatter) => formatter.format(value)
          }
  }

  def exportAsString(converted: Boolean): this.type = {
    convertToString = converted
    this
  }

  def writeTo(os: OutputStream): Unit = {
    this.exporter.exportData(os, this)
  }

}
