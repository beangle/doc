package org.beangle.doc.pdf.cdt

import org.beangle.commons.lang.time.Stopwatch
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
          val sw = new Stopwatch(true)
          converter.convert(URI.create(urls(i)), new File(s"temp${i}.pdf"), options)
          println(sw)
        }
      })
    }
    exe.shutdown()


    converter.close()
  }
}
