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

abstract class DomNode extends Element {
  var parent: Option[DomNode] = None
  var children: Seq[Element] = Seq.empty
  var attributes: Map[String, String] = Map.empty
  var style: Style = Style.empty //computed style
  var classNames: Seq[String] = Seq.empty

  def name: String

  def addStyle(name: String, value: String): Unit = {
    style = style.add(name, value)
  }

  final override def render(sheets: StyleSheets): Unit = {
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

  def appendXml(elem: Element, buf: mutable.StringBuilder, indentation: Int): Unit = {
    elem match
      case t: Text => buf.append(t.value)
      case node: DomNode =>
        val spaces = " " * indentation
        buf ++= s"${spaces}<${node.name}"
        node.attributes foreach { case (k, v) =>
          buf ++= s""" $k="$v""""
        }
        if (node.children.isEmpty) {
          buf ++= "/>\n"
        } else {
          if (node.children.size == 1 && node.children.head.isInstanceOf[Text]) {
            buf ++= s"${node.children.head.asInstanceOf[Text].value}</${node.name}>\n"
          } else {
            buf ++= ">\n"
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

  def add(child: Element): Unit = {
    this.children = this.children :+ child
    child match {
      case d: DomNode => d.parent = Some(this)
      case t: Any =>
    }
  }

  private def matches(searchPattern: String): Boolean = {
    if searchPattern.charAt(0) == '.' then classNames.contains(searchPattern.substring(1))
    else name == searchPattern
  }

  /** 按照tagName或者className查找
   *
   * @param pattern
   * @return
   */
  def find(pattern: String): Seq[DomNode] = {
    val rs = Collections.newBuffer[DomNode]
    val nodes = childNodes
    rs.addAll(nodes.filter(x => x.matches(pattern)))
    nodes.foreach { c =>
      rs.addAll(c.find(pattern))
    }
    rs.toSeq
  }

  def childNodes: Seq[DomNode] = children.filter(_.isInstanceOf[DomNode]).map(_.asInstanceOf[DomNode])

  final def text: Option[String] = {
    val texts = children.filter(_.isInstanceOf[Text]).map(_.asInstanceOf[Text].value)
    if texts.isEmpty then None
    else Some(texts.mkString("\n"))
  }

}
