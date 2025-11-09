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

import org.beangle.template.freemarker.{DefaultTemplateEngine, DefaultTemplateInterpreter}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class DocTemplateTest extends AnyFunSpec, Matchers {
  val data = Map("param1" -> "value1", "param2" -> "value2")

  describe("DocTemplate replace function") {

    it("replace normal") {
      DefaultTemplateInterpreter.process("${param1} is fine", data) should equal("value1 is fine")
    }

    it("replace normal multiple") {
      DefaultTemplateInterpreter.process("#img#${param1} != ${param2}", data) should equal("#img#value1 != value2")
    }

    it("replace expression with space") {
      DefaultTemplateInterpreter.process("${param1 } != ${ param2}", data) should equal("value1 != value2")
    }
    it("splitImg") {
      val template = new DocTemplate(null)
      val rs1 = template.splitImg("申请人签名：${dd}")
      val rs2 = template.splitImg("申请人签名：[#img src=step0_esign height=\"10mm\" width=\"30mm\" /]${step0_auditAt}[#img src=step0_esign height=\"10mm\" width=\"30mm\" /]")
      assert(rs1._1 == "申请人签名：${dd}")
      assert(rs1._2.isEmpty)
      println(rs2._2.size == 2)
    }
  }
}
