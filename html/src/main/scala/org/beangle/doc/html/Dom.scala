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

object Dom {

  class Body extends DomNode

  class UL extends DomNode

  class Li extends DomNode

  class P extends DomNode {
    /** 合并内部的相邻的类似的span
     */
    def mergeSpan(): Unit = {
      if (children.size > 1) {
        val headAttrs = children.head
        match
          case n: DomNode => n.attributes
          case _ => Map.empty

        val allSimpleSpan = children.forall { x =>
          x match
            case x: Span => x.attributes == headAttrs
            case _ => false
        }
        if allSimpleSpan then
          val ns = new Span()
          ns.attributes = headAttrs
          children.foreach {
            case x: Span => x.children foreach { c => ns.append(c) }
          }
          children = Seq(ns)
      }
    }
  }

  class Span extends DomNode {
    def this(child: Element) = {
      this()
      this.append(child)
    }

    def this(text: String, font: Option[Font] = None) = {
      this()
      append(Text(text))
    }
  }

  class Elem(override val name: String) extends DomNode {

  }

  def wrap(name: String, child: Element): DomNode = {
    val elem = new Elem(name)
    elem.append(child)
    elem
  }

  def wrap(name: String, text: String): DomNode = {
    val elem = new Elem(name)
    elem.append(Text(text))
    elem
  }

  class Img extends DomNode {
    override def nonVoid: Boolean = true
  }

  class Text(val value: String) extends Element {
    def render(sheets: StyleSheets): Unit = {}
  }

  class Br extends DomNode {
    override def nonVoid: Boolean = true
  }
}
