package org.beangle.doc.excel.html.dom

import org.apache.poi.ss.usermodel.FontUnderline
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

  def underline: Option[FontUnderline] = {
    properties.get("text-decoration") match {
      case None => None
      case Some(decoration) =>
        var style = ""
        if (decoration.contains("underline")) {
          style = Strings.substringAfter(decoration, "underline").trim()
        } else {
          style = properties.getOrElse("text-decoration-style", "").trim()
        }
        style match
          case "solid" => Some(FontUnderline.SINGLE)
          case "double" => Some(FontUnderline.DOUBLE)
          case _ => None
    }
  }

  def bold: Option[String] = {
    properties.get("font-weight")
  }


  override def toString: String = {
    properties.map(x => s"${x._1}:${x._2}").toSeq.sorted.mkString(";")
  }
}
