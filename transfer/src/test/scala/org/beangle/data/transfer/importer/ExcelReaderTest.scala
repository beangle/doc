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

package org.beangle.doc.transfer.importer

import org.beangle.commons.lang.ClassLoaders
import org.beangle.doc.transfer.Format
import org.beangle.doc.transfer.importer.ExcelReader
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ExcelReaderTest extends AnyFunSpec with Matchers {
  describe("ExcelReader") {
    it("reader") {
      val template = ClassLoaders.getResource("data.xlsx").get
      val reader = new ExcelReader(template.openStream(),0,Format.Xlsx)
      val title = reader.readAttributes()
      val data = reader.read()
      println(title)
      println(data.toSeq)
    }
  }

}
