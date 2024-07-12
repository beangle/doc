package org.beangle.doc.excel.html.dom

case class Style(properties: Map[String, String]) {
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
}
