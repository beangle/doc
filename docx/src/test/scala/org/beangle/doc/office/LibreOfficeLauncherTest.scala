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

package org.beangle.doc.office

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.io.File

class LibreOfficeLauncherTest extends AnyFunSpec, Matchers {

  describe("LibreOfficeLauncher.Configuration") {
    it("defaults to auto port and 60s connect timeout") {
      val cfg = new LibreOfficeLauncher.Configuration
      cfg.port should equal(0)
      cfg.connectTimeout.getSeconds should equal(60L)
    }
  }

  describe("LibreOfficeConverter without init") {
    it("rejects convertToPdf") {
      val converter = new LibreOfficeConverter
      an[IllegalArgumentException] should be thrownBy
        converter.convertToPdf(new File("in.docx"), new File("out.pdf"))
    }

    it("destroy without init is a no-op") {
      val converter = new LibreOfficeConverter
      noException should be thrownBy converter.destroy()
    }
  }
}
