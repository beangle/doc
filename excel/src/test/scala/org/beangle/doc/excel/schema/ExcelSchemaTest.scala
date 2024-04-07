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

package org.beangle.doc.excel.schema

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.io.{File, FileOutputStream}
import java.time.YearMonth

class ExcelSchemaTest extends AnyFunSpec with Matchers {

  private val genderNames = List("男", "女")
  private val idTypeNames = List("中华人民共和国居民身份证", "护照")

  describe("TemplateGenerator") {
    it("Generator") {
      val file = File.createTempFile("template", ".xlsx")
      val schema = new ExcelSchema()
      val sheet = schema.createScheet("数据模板")
      sheet.title("人员信息模板")
      sheet.remark("特别说明：\r\n1、不可改变本表格的行列结构以及批注，否则将会导入失败！\n2、必须按照规格说明的格式填写。\n3、可以多次导入，重复的信息会被新数据更新覆盖。\n4、保存的excel文件名称可以自定。")
      sheet.add("姓名", "name").length(32).required().remark("≤32位")
      sheet.add("性别", "gender").ref(genderNames)
      sheet.add("证件类型", "id_type").ref(idTypeNames)
      sheet.add("证件", "id").min(15).max(18).unique().remark("身份证必须是15位或18位\n否则请选其他")
      sheet.add("工资", "salary").decimal("#,##0.00")
      sheet.add("出生日期", "birthday").date()
      sheet.add("出生年月", "birthYearMonth").yearMonth()
      sheet.add("出生日期时间", "birthDatetime").datatime()
      sheet.add("出生时间", "birthTime").time().min("08:00:00").max("12:00:00")
      sheet.add("是否高管", "is_manager").bool()
      sheet.add("联系地址", "address").length(100)
      sheet.add("子女数目", "children_count").integer(0, 8)
      sheet.add("毕业年月","graduated_on").asType(classOf[YearMonth])
      val os = new FileOutputStream(file)
      schema.generate(os)
      println("template located in " + file.getAbsolutePath)
    }
  }

}
