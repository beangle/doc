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

package org.beangle.doc.transfer.exporter

import org.beangle.commons.lang.SystemInfo
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.io.{File, FileOutputStream}

class ExcelWriterTest extends AnyFunSpec with Matchers {
  describe("ExcelWriter") {
    it("writer") {
      val tmpFile = new File(SystemInfo.tmpDir + "/test.xlsx")
      val writer = new ExcelWriter(new FileOutputStream(tmpFile))
      writer.writeHeader(Some("2023 年度 人员工资明细"), Array("姓名", "工资", "奖金", "扣税", "五险一金合计"))
      writer.write(Array("张三", "10000", "1000", "1000", "1000"))
      writer.write(Array("李四", "20000", "2000", "2000", "2000"))
      writer.write(Array("王五", "30000", "3000", "3000", "3000"))
      writer.close()
      println("Excel文件已生成：" + tmpFile.getAbsolutePath)
      tmpFile.deleteOnExit()
    }
  }

}
