package org.beangle.doc.excel.html.dom

import org.beangle.commons.lang.Strings

case class Text(html: String, font: Option[Font]) {
  def texts: String = {
    var t = html
    t = Strings.replace(t, "<br/>", "\n")
    t
  }

  override def toString: String = html
}
