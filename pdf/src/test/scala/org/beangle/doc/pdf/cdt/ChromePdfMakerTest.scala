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

import java.io.File
import java.net.URI
import java.util.concurrent.Executors

object ChromePdfMakerTest {

  def main(args: Array[String]): Unit = {
    val urls = Array("https://www.oschina.net/news/248319/docker-24-0-3-released",
      "https://stacktuts.com/how-to-open-new-incognito-window-with-javascript-google-chrome",
      "https://www.oschina.net/news/260444/android-14")

    val options = PrintOptions.defaultOptions
    options.shrinkTo1Page = false
    options.scale = 0.8d
    options.margin = PageMargin.Default
    val converter = new SPDConverter(new ChromePdfMaker)

    val exe = Executors.newFixedThreadPool(3)
    (0 to 2) foreach { i =>
      exe.submit(new Runnable() {
        override def run(): Unit = {
          converter.convert(URI.create(urls(i)), new File(s"target/temp${i}.pdf"), options)
        }
      })
    }
    exe.shutdown()
    converter.close()
  }
}
