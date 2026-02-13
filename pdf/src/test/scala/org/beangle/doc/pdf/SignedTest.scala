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

package org.beangle.doc.pdf

import org.beangle.commons.lang.ClassLoaders
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.io.File
import java.nio.file.Files

class SignedTest extends AnyFunSpec with Matchers {

  describe("Signed") {

    it("hasSignature returns false for PDF without signatures") {
      val origin = resourceFile("origin.pdf")
      Signed.hasSignature(origin) should be(false)
    }

    it("getSignatureTimes returns empty for PDF without signatures") {
      val origin = resourceFile("origin.pdf")
      Signed.getSignatureTimes(origin) should be(empty)
    }

    it("getSignatureInfos returns empty for PDF without signatures") {
      val origin = resourceFile("origin.pdf")
      Signed.getSignatureInfos(origin) should be(empty)
    }

    it("hasSignature returns true for PDF with signatures") {
      val signed = resourceFile("signed.pdf")
      Signed.hasSignature(signed) should be(true)
    }

    it("getSignatureTimes returns signing times for signed PDF") {
      val signed = resourceFile("signed.pdf")
      val times = Signed.getSignatureTimes(signed)
      times should not be empty
    }

    it("getSignatureInfos returns detailed info for signed PDF") {
      val signed = resourceFile("signed.pdf")
      val infos = Signed.getSignatureInfos(signed)
      infos should not be empty
      infos.foreach { info =>
        info.fieldName should not be empty
        info.signTime should not be null
      }
    }

    it("removeAllSignatures removes all signatures") {
      val signed = resourceFile("signed.pdf")

      val tempFile = Files.createTempFile("signed-", ".pdf")
      try {
        Files.copy(signed.toPath, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        val toProcess = tempFile.toFile

        Signed.hasSignature(toProcess) should be(true)
        Signed.removeAllSignatures(toProcess)
        Signed.hasSignature(toProcess) should be(false)
      } finally {
        Files.deleteIfExists(tempFile)
      }
    }
  }

  private def resourceFile(name: String): File = {
    ClassLoaders.getResource(name) match {
      case Some(url) => new File(url.toURI)
      case None => throw IllegalArgumentException(s"Cannot find resource file ${name}")
    }
  }
}
