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

object Style {
  def apply(properties: Map[String, String]): Style = {
    new Style(properties)
  }

  def empty: Style = {
    Style(Map.empty)
  }

  def parse(value: String): Map[String, String] = {
    val texts = Strings.split(value, ";")
    texts.map { text =>
      val key = Strings.substringBefore(text, ":")
      val value = Strings.substringAfter(text, ":")
      (key.trim, value.trim)
    }.toMap
  }
}

class Style(val properties: Map[String, String]) {
  def inheritables: Map[String, String] = {
    properties.filter { x =>
      !x._1.startsWith("margin") &&
        !x._1.startsWith("padding") &&
        !x._1.startsWith("width") &&
        !x._1.startsWith("height") &&
        !x._1.startsWith("border") &&
        !x._1.startsWith("position") &&
        !x._1.startsWith("background")
    }
  }

  def has(name: String, value: String): Boolean = {
    properties.get(name).contains(value)
  }

  def has(name: String): Boolean = {
    properties.contains(name)
  }

  def height: Option[Length] = {
    properties.get("height") match
      case Some(w) => Some(Length(w))
      case None => None
  }

  def width: Option[Length] = {
    properties.get("width") match
      case Some(w) => Some(Length(w))
      case None => None
  }

  def textAlign: Option[String] = {
    properties.get("text-align")
  }

  def verticalAlign: Option[String] = {
    properties.get("vertical-align")
  }

  override def toString: String = {
    properties.map(x => s"${x._1}:${x._2}").toSeq.sorted.mkString(";")
  }

  def font: Option[Font] = {
    val fontProps = properties.filter(x => x._1.startsWith("font-") || x._1.startsWith("text-decoration"))
    if (fontProps.isEmpty) None else Some(Font(fontProps))
  }

  def border: Option[Border] = {
    val props = properties.filter(x => x._1.startsWith("border") || x._1.startsWith("border-"))
    if (props.isEmpty) None else Some(Border(props))
  }

  def add(name: String, value: String): Style = {
    if (this.properties.isEmpty) {
      Style(Map(name -> value))
    } else {
      val map = Collections.newMap[String, String]
      map.addAll(this.properties)
      map.put(name, value)
      Style(map.toMap)
    }
  }
}
