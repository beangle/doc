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

package org.beangle.doc.excel.html

import org.beangle.doc.html.BorderData
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class StylesTest extends AnyFunSuite with Matchers {

  test("border colors use ARGB") {
    val (_, color) = Styles.convertBorder(new BorderData("top", "1px", "solid", "#DDD9C4")).get
    color.getARGB shouldBe Array[Byte](0xFF.toByte, 0xDD.toByte, 0xD9.toByte, 0xC4.toByte)
  }

  test("default border colors use ARGB") {
    val (_, color) = Styles.convertBorder(new BorderData("top", "1px", "solid", "black")).get
    color.getARGB shouldBe Array[Byte](0xFF.toByte, 0, 0, 0)
  }
}
