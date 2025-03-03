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

package org.beangle.doc.html

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.Strings

import scala.collection.mutable

/** HTML DOM Node
 */
abstract class DomNode extends Element {
  var parent: Option[DomNode] = None
  var children: Seq[Element] = Seq.empty
  var attributes: Map[String, String] = Map.empty
  var computedStyle: Style = Style.empty //computed style
  var classNames: Seq[String] = Seq.empty

  def name: String = {
    val className = getClass.getName
    val dollarIdx = className.lastIndexOf('$')
    if (dollarIdx > 0) {
      className.substring(dollarIdx + 1).toLowerCase()
    } else {
      Strings.substringAfterLast(className, ".").toLowerCase()
    }
  }

  def addStyle(name: String, value: Any): Unit = {
    computedStyle = computedStyle.add(name, value.toString)
    val styles = attributes.getOrElse("style", "")
    val values = if (Strings.isEmpty(styles)) Map.empty else Style.parse(styles)
    addAttribute("style", Style(values.updated(name, value.toString)).toString)
  }

  def addStyles(styles: String): Unit = {
    Style.parse(styles) foreach { case (k, v) =>
      computedStyle = computedStyle.add(k, v)
    }

    val s = if styles.endsWith(";") then styles else styles + ";"
    val n = attributes.getOrElse("style", "") + s
    val values = Style.parse(n)
    addAttribute("style", Style(values).toString)
  }

  def addClass(name: String): Unit = {
    attributes.get("class") match
      case None => addAttribute("class", name)
      case Some(clz) => addAttribute("class", s"${clz} ${name}")
  }

  final override def render(sheets: StyleSheets): Unit = {
    renderStyle(sheets)
    children.foreach(_.render(sheets))
  }

  def renderStyle(sheets: StyleSheets): Unit = {
    classNames = getClassNames
    val props = Collections.newMap[String, String]
    this.parent foreach { p =>
      props.addAll(p.computedStyle.inheritables)
    }
    sheets.matches(this) foreach { ss =>
      props.addAll(ss.properties)
    }
    attributes.get("style") foreach { value =>
      props.addAll(Style.parse(value))
    }
    this.computedStyle = Style(props.toMap)
  }

  def outerHtml: String = {
    val buf = new StringBuilder("")
    appendXml(this, buf)
    buf.toString
  }

  def innerHTML: String = {
    val buf = new StringBuilder("")
    if (this.children.nonEmpty) {
      if (this.children.size == 1 && this.children.head.isInstanceOf[Dom.Text]) {
        buf ++= s"${this.children.head.asInstanceOf[Dom.Text].value}"
      } else {
        this.children foreach (appendXml(_, buf))
      }
    }
    buf.toString
  }

  def appendXml(elem: Element, buf: mutable.StringBuilder): Unit = {
    elem match
      case t: Dom.Text => buf.append(t.value)
      case node: DomNode =>
        buf ++= s"<${node.name}"
        node.attributes foreach { case (k, v) =>
          buf ++= s""" $k="$v""""
        }
        if (node.children.isEmpty) {
          if node.nonVoid then buf ++= "/>"
          else buf ++= s"></${node.name}>"
        } else {
          if (node.children.size == 1 && node.children.head.isInstanceOf[Dom.Text]) {
            buf ++= s">${node.children.head.asInstanceOf[Dom.Text].value}</${node.name}>"
          } else {
            buf ++= ">"
            node.children foreach (appendXml(_, buf))
            buf ++= s"</${node.name}>"
          }
        }
  }

  private def getClassNames: Seq[String] = {
    attributes.get("class") match
      case None => Seq.empty
      case Some(s) => Strings.split(s, ' ').toSeq
  }

  def remove(child: Element): Unit = {
    val idx = children.indexOf(child)
    if idx >= 0 then {
      children = children.filterNot(_ == child)
    }
  }

  def prepend(child: Element): Unit = {
    this.children = child +: this.children
    child match {
      case d: DomNode => d.parent = Some(this)
      case t: Any =>
    }
  }

  def append(child: Element): Unit = {
    this.children = this.children :+ child
    child match {
      case d: DomNode => d.parent = Some(this)
      case t: Any =>
    }
  }

  def firstChild[T](t: Class[T]): Option[T] = {
    children.find(x => t == x.getClass).map(_.asInstanceOf[T])
  }

  def lastChild[T](t: Class[T]): Option[T] = {
    children.findLast(x => t == x.getClass).map(_.asInstanceOf[T])
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

  final def text: String = {
    children.map {
      case t: Dom.Text => t.value
      case d: DomNode => d.text
    }.mkString("\n")
  }

  /** Non-void element using self-closing syntax.
   *
   * @return
   */
  def nonVoid: Boolean = false

  def addAttribute(name: String, value: String): Unit = {
    attributes = attributes.updated(name, value)
  }

  override def toString: String = {
    name
  }
}
