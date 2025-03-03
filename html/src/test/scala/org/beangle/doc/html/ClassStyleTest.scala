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

import org.beangle.doc.html.ClassStyle
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ClassStyleTest extends AnyFunSpec with Matchers {

  describe("ClassStyle") {
    it("build chain") {
      val chain = ClassStyle.buildChain("div.plan-table > tbody.grid-body  tr .comment")
      chain.tagName should equal("*")
      chain.className should equal("comment")

      chain.ancestor.nonEmpty should be(true)
      chain.ancestor.get.isParent should be(false)
      val p2 = chain.ancestor.get.ancestor
      p2.tagName should equal("tr")
      p2.className should equal("*")

      p2.ancestor.nonEmpty should be(true)
      p2.ancestor.get.isParent should be(false)
      val p3 = p2.ancestor.get.ancestor
      p3.tagName should equal("tbody")
      p3.className should equal("grid-body")

      p3.ancestor.nonEmpty should be(true)
      p3.ancestor.get.isParent should be(true)
      val p4 = p3.ancestor.get.ancestor
      p4.tagName should equal("div")
      p4.className should equal("plan-table")
    }
  }
}
