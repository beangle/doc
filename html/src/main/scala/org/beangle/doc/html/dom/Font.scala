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

package org.beangle.doc.html.dom

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.Strings

case class Font(properties: Map[String, String]) {
  def families: Seq[String] = {
    properties.get("font-family") match
      case None => Seq.empty
      case Some(f) =>
        var ft = Strings.replace(f, "'", "")
        ft = Strings.replace(ft, "\"", "")
        Strings.split(ft, ",").toSeq
  }

  def asciiFont: Option[Font] = {
    properties.get("font-family") match
      case None => None
      case Some(f) =>
        val map = Collections.newMap[String, String]
        map.addAll(properties)
        var ft = Strings.replace(f, "'", "")
        ft = Strings.replace(ft, "\"", "")
        Strings.split(ft, ",").head
        map.put("font-family", Strings.split(ft, ",").head)
        Some(Font(map.toMap))
  }

  def family: Option[String] = {
    properties.get("font-family") match
      case None => None
      case Some(f) =>
        var ft = Strings.replace(f, "'", "")
        ft = Strings.replace(ft, "\"", "")
        Strings.split(ft, ",").lastOption
  }

  def size: Option[Short] = {
    properties.get("font-size") match
      case None => None
      case Some(s) =>
        var fs = s
        fs = Strings.replace(fs, "pt", "")
        Some(fs.toShort)
  }

  def italic: Option[Boolean] = {
    properties.get("font-style").map(_ == "italic")
  }

  def strikeout: Option[Boolean] = {
    properties.get("text-decoration").map(x => x.contains("line-through"))
  }

  def underline: Option[String] = {
    properties.get("text-decoration") match {
      case None => None
      case Some(decoration) =>
        var style: String = null
        if (decoration.contains("underline")) {
          style = Strings.substringAfter(decoration, "underline").trim()
        } else {
          style = properties.getOrElse("text-decoration-style", "").trim()
        }
        Option(style)
    }
  }

  def bold: Option[String] = {
    properties.get("font-weight")
  }

  override def toString: String = {
    properties.map(x => s"${x._1}:${x._2}").toSeq.sorted.mkString(";")
  }
}
