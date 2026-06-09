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

package org.beangle.doc.pdf.cdt

import org.beangle.doc.core.{PageMargin, PrintOptions}
import org.beangle.doc.pdf.SPDConverter
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.io.File
import java.net.URI
import java.time.Duration

class ChromePdfMakerTest extends AnyFunSpec with Matchers {

  private val urls = Array(
    "https://www.oschina.net/news/248319/docker-24-0-3-released",
    "https://www.oschina.net/news/260444/android-14"
  )

  private def options: PrintOptions = {
    val o = PrintOptions.defaultOptions
    o.shrinkTo1Page = false
    o.scale = 0.8d
    o.margin = PageMargin.Default
    o.pageRanges = Some("1-2")
    o.renderDelay = Duration.ofSeconds(2)
    o
  }

  private def outDir: File = {
    val dir = new File("pdf/target")
    dir.mkdirs()
    dir
  }

  describe("ChromePdfMaker") {
    it("converts news pages to PDF") {
      assume(ChromePdfMaker.isAvailable, "Chrome is not available")
      val converter = new SPDConverter(new ChromePdfMaker)
      try {
        urls.zipWithIndex.foreach { case (url, i) =>
          val out = new File(outDir, s"temp$i.pdf")
          withClue(s"converting $url: ") {
            converter.convert(URI.create(url), out, options) should be(true)
            out.exists() should be(true)
            out.length() should be > 10000L
          }
        }
      } finally {
        converter.close()
      }
    }
  }
}
