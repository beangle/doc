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

package org.beangle.doc.docx

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.beangle.commons.lang.{Chars, Strings}

import java.io.ByteArrayOutputStream
import java.net.URL

object DocHelper {

  def toDoc(url: URL, data: collection.Map[String, String]): Array[Byte] = {
    val templateIs = url.openStream()
    val doc = new XWPFDocument(templateIs)
    import scala.jdk.javaapi.CollectionConverters.*

    for (p <- asScala(doc.getParagraphs)) {
      val runs = p.getRuns
      if (runs != null) {
        for (r <- asScala(runs)) {
          val text = r.getText(0)
          if (text != null && text.contains("${")) r.setText(replace(text, data), 0)
        }
      }
    }

    for (tbl <- asScala(doc.getTables)) {
      for (row <- asScala(tbl.getRows)) {
        for (cell <- asScala(row.getTableCells)) {
          for (p <- asScala(cell.getParagraphs)) {
            for (r <- asScala(p.getRuns)) {
              val text = r.getText(0)
              if (text != null && text.contains("${")) {
                var processed = replace(text, data)
                if (text.startsWith("[#maxlen")) {
                  val max = Integer.parseInt(Strings.substringBetween(text, "[#maxlen", "]").trim())
                  processed = processed.substring(processed.indexOf(']') + 1)
                  val resultLen = Chars.charLength(processed)
                  if (resultLen > max) {
                    val scale = java.lang.Double.valueOf(max * 100.0 / resultLen).toInt
                    r.setTextScale(scale)
                  }
                }
                r.setText(processed, 0)
              }
            }
          }
        }
      }
    }
    val bos = new ByteArrayOutputStream()
    doc.write(bos)
    templateIs.close()
    bos.toByteArray
  }

  private[docx] def replace(template: String, data: collection.Map[String, String]): String = {
    var text = template
    while (text.contains("${")) {
      val k = Strings.substringBetween(text, "${", "}").trim()
      val v = data.getOrElse(k, "")
      val begin = text.indexOf("${")
      val end = text.indexOf("}") + 1
      text = Strings.replace(text, text.substring(begin, end), v)
    }
    text
  }
}
