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

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class DocHelperTest extends AnyFunSpec with Matchers {
  val data = Map("param1" -> "value1", "param2" -> "value2")

  describe("DocHelper replace function") {
    it("replace normal") {
      DocHelper.replace("${param1} is fine", data) should equal("value1 is fine")
    }

    it("replace normal multiple") {
      DocHelper.replace("${param1} != ${param2}", data) should equal("value1 != value2")
    }

    it("replace expression with space") {
      DocHelper.replace("${param1 } != ${ param2}", data) should equal("value1 != value2")
    }
  }
}
