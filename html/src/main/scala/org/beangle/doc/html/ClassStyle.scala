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

import org.beangle.commons.lang.Strings
import org.beangle.doc.html.ClassStyle.ClassEntry

object ClassStyle {

  def buildChain(name: String): ClassEntry = {
    val n = name.replaceAll("(\\s)+>(\\s)+", ">")
    val parts = Strings.split(n, ' ')
    var ancestor: Option[ClassEntry] = None
    var start: ClassEntry = null

    parts foreach { part =>
      var isParent = false
      var i = 0
      Strings.split(part, '>') foreach { p =>
        isParent = i > 0
        val tagName = Strings.substringBefore(p, ".").trim
        val className = Strings.substringAfter(p, ".").trim
        val entry = ClassEntry(if Strings.isBlank(tagName) then "*" else tagName,
          if Strings.isBlank(className) then "*" else className,
          if ancestor.isEmpty then None else Some(ClassAncestor(isParent, ancestor.get))
        )
        ancestor = Some(entry)
        start = entry
        i += 1
      }
    }
    start
  }

  case class ClassEntry(tagName: String, className: String, ancestor: Option[ClassAncestor])

  case class ClassAncestor(isParent: Boolean, ancestor: ClassEntry)

}

class ClassStyle(name: String, properties: Map[String, String]) extends Style(properties) {

  val chain: ClassEntry = ClassStyle.buildChain(name)

  override def toString: String = {
    val ps = properties.map(x => s"${x._1}:${x._2}").toSeq.sorted.mkString(";")
    s"${name} {${ps}}"
  }

  def matches(node: DomNode): Boolean = {
    if (chain.ancestor.isEmpty) {
      if (name.charAt(0) == '#') {
        node.attributes.get("id").contains(name.substring(1))
      } else if (name.charAt(0) == '.') {
        node.classNames.contains(name.substring(1))
      } else {
        matchByChain(node)
      }
    } else {
      matchByChain(node)
    }
  }

  private def matchByChain(node: DomNode): Boolean = {
    var curClass = chain
    var curNode = node
    var matched = matches(curClass, curNode)
    while (matched && curClass != null) {
      curClass.ancestor match
        case None => curClass = null
        case Some(ancestor) =>
          curClass = ancestor.ancestor
          val ancestorNode = findAncestorNode(curClass, curNode, ancestor.isParent)
          if (ancestorNode.isEmpty) {
            matched = false
          } else {
            curNode = ancestorNode.get
            matched = matches(curClass, curNode)
          }
    }
    matched
  }

  /** 查找指定clazz的上级节点
   *
   * @param clz
   * @param start
   * @param isParent 是否是直接上级
   * @return
   */
  private def findAncestorNode(clz: ClassEntry, start: DomNode, isParent: Boolean): Option[DomNode] = {
    if (isParent) {
      start.parent
    } else {
      var cur = start.parent.orNull
      var parentNode: DomNode = null
      while (null != cur && null == parentNode) {
        if (matches(clz, cur)) {
          parentNode = cur
        }
        cur = cur.parent.orNull
      }
      Option(parentNode)
    }
  }

  private def matches(curClass: ClassEntry, curNode: DomNode): Boolean = {
    (curClass.tagName == "*" || curClass.tagName == curNode.name) &&
      (curClass.className == "*" || curNode.classNames.contains(curClass.className))
  }

  def toString(indentation: Int): String = {
    val iden1 = " " * (indentation)
    val iden2 = " " * (indentation + 2)
    val ps = properties.map(x => s"${iden2}${x._1}:${x._2}").toSeq.sorted.mkString(";\n")
    s"${iden1}${name} {\n${ps}\n${iden1}}"
  }
}
