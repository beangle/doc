package org.beangle.doc.excel.html.dom

import org.beangle.commons.collection.Collections

import scala.collection.mutable


abstract class DomNode {
  var parent: Option[DomNode] = None
  var style: Option[Style] = None

  def attributes: Map[String, String] = {
    if style.isEmpty then Map.empty
    else Map("style" -> style.get.toString)
  }

  def addStyle(name: String, value: String): Unit = {
    style match
      case None => style = Some(Style(Map(name -> value)))
      case Some(s) =>
        val map = Collections.newMap[String, String]
        map.addAll(s.properties)
        map.put(name, value)
        style = Some(Style(map.toMap))
  }

  def name: String

  def text: Option[Text] = None

  def children: collection.Seq[DomNode] = Seq.empty

  def computedStyle: Style = {
    val parentStyles = Collections.newBuffer[Style]
    parentStyles.addAll(this.style)
    var current = this.parent
    while (current.nonEmpty) {
      val c = current.get
      parentStyles.addAll(c.style)
      current = c.parent
    }
    val props = Collections.newMap[String, String]
    parentStyles.reverse foreach { s =>
      props.addAll(s.properties)
    }
    Style(props.toMap)
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
}
