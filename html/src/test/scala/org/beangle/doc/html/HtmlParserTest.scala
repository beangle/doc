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

import org.beangle.commons.io.IOs
import org.beangle.commons.lang.ClassLoaders
import org.beangle.doc.html.dom.Table
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class HtmlParserTest extends AnyFunSpec with Matchers {

  describe("HtmlParser") {
    it("parse html") {
      val t = IOs.readString(ClassLoaders.getResourceAsStream("table.html").head)
      val doc = HtmlParser.parse(t)
      val body = doc.children.find(_.name == "body").get
      body.style.font.nonEmpty should be(true)
      val groups = body.find("colgroup")
      groups.size should be(1)
      val group = groups.head.asInstanceOf[Table.ColGroup]
      group.cols.size should be(13)
      val col1 = group.cols.head
      col1.width should equal(Some("32px"))
      col1.attributes.size should be(1)
      col1.attributes.contains("width") should be(true)

      val modules = body.find(".level2module")
      modules.size should be(1)
      val cell = modules.head.asInstanceOf[Table.Cell]
      cell.colspan should be(13)
      cell.style.has("height", "31.85pt") should be(true)

      //println(doc.toHtml)
    }
  }
}
