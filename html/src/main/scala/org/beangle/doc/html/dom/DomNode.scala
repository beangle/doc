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

import scala.collection.mutable

abstract class DomNode {
  var parent: Option[DomNode] = None
  var children: Seq[DomNode] = Seq.empty
  var attributes: Map[String, String] = Map.empty
  var style: Style = Style.empty //computed style
  var classNames: Seq[String] = Seq.empty

  def name: String

  def text: Option[Text] = None

  def addStyle(name: String, value: String): Unit = {
    style = style.add(name, value)
  }

  final def render(sheets: StyleSheets): Unit = {
    renderStyle(sheets)
    children.foreach(_.render(sheets))
  }

  def renderStyle(sheets: StyleSheets): Unit = {
    classNames = getClassNames
    val props = Collections.newMap[String, String]
    this.parent foreach { p =>
      props.addAll(p.style.inheritables)
    }
    sheets.matches(this) foreach { ss =>
      props.addAll(ss.properties)
    }
    attributes.get("style") foreach { value =>
      props.addAll(Style.parse(value))
    }
    this.style = Style(props.toMap)
  }

  override def toString: String = {
    val buf = new StringBuilder("\n")
    appendXml(this, buf, 0)
    buf.toString
  }

  def appendXml(node: DomNode, buf: mutable.StringBuilder, indentation: Int): Unit = {
    val spaces = " " * indentation
    buf ++= s"${spaces}<${node.name}"
    node.attributes foreach { case (k, v) =>
      buf ++= s""" $k="$v""""
    }
    node.text match {
      case None =>
        if (node.children.isEmpty) {
          buf ++= "/>\n"
        } else {
          buf ++= ">\n"
          node.children foreach (appendXml(_, buf, indentation + 2))
          buf ++= s"${spaces}</${node.name}>\n"
        }
      case Some(t) =>
        if (node.children.isEmpty) {
          buf ++= s">${t}</${node.name}>\n"
        } else {
          buf ++= ">\n"
          buf ++= t.html
          node.children foreach (appendXml(_, buf, indentation + 2))
          buf ++= s"${spaces}</${node.name}>\n"
        }
    }
  }

  private def getClassNames: Seq[String] = {
    attributes.get("class") match
      case None => Seq.empty
      case Some(s) => Strings.split(s, ' ').toSeq
  }

  def add(child: DomNode): Unit = {
    child.parent = Some(this)
    this.children = this.children :+ child
  }

  private def matches(searchPattern: String): Boolean = {
    if searchPattern.charAt(0) == '.' then classNames.contains(searchPattern.substring(1))
    else name == searchPattern
  }

  /** 按照tagName或者className查找
   * @param pattern
   * @return
   */
  def find(pattern: String): Seq[DomNode] = {
    val rs = Collections.newBuffer[DomNode]
    rs.addAll(children.filter(x => x.matches(pattern)))
    children.foreach { c =>
      rs.addAll(c.find(pattern))
    }
    rs.toSeq
  }
}
