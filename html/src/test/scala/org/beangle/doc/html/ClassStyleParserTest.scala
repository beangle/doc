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

import org.beangle.doc.html.dom.Table.TBody
import org.beangle.doc.html.dom.{Body, Style, StyleSheets, Table}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ClassStyleParserTest extends AnyFunSpec with Matchers {

  describe("ClassStyleParser") {
    it("parse css") {
      val a =
        """
          |  .plan-table{
          |    text-align:center;
          |    border-collapse: collapse;
          |    margin:auto;
          |  }
          |  .plan-table td,.plan-table th{
          |    border:1px solid black;
          |  }
          |  body{
          |    font-family:'Times New Roman',宋体;
          |    font-size:12pt;
          |  }
          |  td.course{
          |    text-align:left;
          |  }
          |  .level2module{
          |    font-weight:bold;
          |  }
          |""".stripMargin
      val styles = ClassStyleParser.parse(a)
      styles.nonEmpty should be(true)
      styles.size should equal(6)

      val sheets = StyleSheets(styles)
      val body = new Body
      val bodyStyles = sheets.matches(body)
      bodyStyles.length should be(1)

      val table = new Table
      table.classNames = Seq("plan-table")
      val tbody = new TBody
      table.add(tbody)
      var tr = new Table.Row
      val td = new Table.Cell
      tbody.add(tr)
      tr.add(td)
      val tdStyles = sheets.matches(td)
      tdStyles.length should be(1)
    }
  }
}
