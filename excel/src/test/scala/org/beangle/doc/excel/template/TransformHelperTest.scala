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

package org.beangle.doc.excel.template

import org.beangle.commons.lang.ClassLoaders
import org.beangle.doc.excel.CellRef.*
import org.beangle.doc.excel.{CellRef, Workbooks}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.io.FileOutputStream
import java.nio.file.Files
import java.time.MonthDay
import scala.collection.mutable

class TransformHelperTest extends AnyFunSpec with Matchers {

  describe("TransformHelper") {
    it("processTemplate") {
      val context =  Map("datas" -> List("1", "2", "3", "a", "b", "c"))
      val template = ClassLoaders.getResourceAsStream("sample.xlsx").orNull
      val helper = new TransformHelper(template)
      val file = Files.createTempFile("sample", ".xlsx")
      val os = new FileOutputStream(file.toFile, false)
      helper.transform(os, context)
      file.toFile.delete()
    }
    it("generate multisheet") {
      val derek = Employee("Eerek", 35, 3000, 0.30, MonthDay.parse("--09-01"))
      val elsa = Employee("Elsa", 28, 1500, 0.15, MonthDay.parse("--09-02"))
      val oleg = Employee("Oleg", 32, 2300, 0.25, MonthDay.parse("--09-03"))
      val neil = Employee("Neil", 34, 2500, 0, MonthDay.parse("--09-04"))

      val maria = Employee("Maria", 34, 1700, 0.15, MonthDay.parse("--09-04"))
      val john = Employee("John", 35, 2800, 0.20, MonthDay.parse("--09-05"))

      val it = Department("IT", derek, List(elsa, oleg, neil), "http://company.com/it")
      val hr = Department("HR", derek, List(maria, john), "http://company.com/ha")

      val departs = List(it, hr)
      val context= new mutable.HashMap[String,Any]
      context.put("departments", departs)
      context.put("sheetNames", departs.map(_.name))
      val template = ClassLoaders.getResourceAsStream("multisheet_markup_template.xls").orNull
      val file = Files.createTempFile("multisheet_markup_template", ".xlsx")
      val os = new FileOutputStream(file.toFile, false)
      val helper = new TransformHelper(template)
      helper.transform(os, context)
      file.toFile.delete()
    }
  }
}
