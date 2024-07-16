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
import org.beangle.doc.html.dom.ClassStyle

object ClassStyleParser {

  def parse(contents: String): Seq[ClassStyle] = {
    val parts = Strings.split(contents.trim, "}")
    val styles = Collections.newBuffer[ClassStyle]
    parts foreach { part =>
      val name = Strings.substringBefore(part, "{").trim
      val texts = Strings.split(Strings.substringAfter(part, "{").trim, ";")
      val properties = texts.map { text =>
        val key = Strings.substringBefore(text, ":")
        val value = Strings.substringAfter(text, ":")
        (key.trim, value.trim)
      }.toMap

      Strings.split(name, ',') foreach { n =>
        styles.addOne(new ClassStyle(n.trim, properties))
      }
    }
    styles.toSeq
  }

}
