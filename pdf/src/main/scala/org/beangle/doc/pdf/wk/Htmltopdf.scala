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

package org.beangle.doc.pdf.wk

import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import org.beangle.commons.collection.Collections
import org.beangle.doc.core.*

import java.io.{ByteArrayInputStream, File, InputStream}
import java.util.function.Consumer

object Htmltopdf {
  def create(): Htmltopdf = {
    create(Map.empty)
  }

  def create(initSettings: Map[String, String]): Htmltopdf = {
    new Htmltopdf(initSettings)
  }
}

/** 转换HTML到PDF工具类
 * <p>
 * 该库已经很久不维护了，如果使用Node.js,可以考虑使用puppeteer(https://github.com/puppeteer/puppeteer)
 * 他会启动chrome，使用API中的pdf，可以完成转换工作。
 * </p>
 * @see https://github.com/wooio/htmltopdf-java
 * @see https://wkhtmltopdf.org/libwkhtmltox/pagesettings.html
 */
class Htmltopdf {
  private val settings = Collections.newMap[String, String]
  private val pages = Collections.newBuffer[WKPage]
  private val warningCallbacks = Collections.newBuffer[Consumer[String]]
  private val errorCallbacks = Collections.newBuffer[Consumer[String]]
  private val progressCallbacks = Collections.newBuffer[Consumer[ProgressPhase]]
  private val finishedCallbacks = Collections.newBuffer[Consumer[Boolean]]

  def this(initSettings: Map[String, String]) = {
    this()
    initSettings foreach { case (k, v) =>
      if (GlobalSettings.isValid(k)) settings.put(k, v)
    }
  }

  def set(name: String, value: Any): this.type = {
    if (GlobalSettings.isValid(name)) {
      value match {
        case None => this.settings.remove(name)
        case null => this.settings.remove(name)
        case _ => this.settings.put(name, value.toString)
      }
    } else {
      throw new RuntimeException(s"Cannot recognize global settings {$name}")
    }
    this
  }

  def getSetting(name: String): Option[String] = {
    settings.get(name)
  }

  /** 禁止只能缩小策略(WebKit会依据pixel/dpi比例) */
  def disableSmartShrinking(disable: Boolean): this.type = {
    set(GlobalSettings.DisableSmartShrinking, disable)
  }

  /** 纸张大小(A3,A4,A5..) */
  def pageSize(pageSize: PageSize): this.type = {
    set(GlobalSettings.PageSize, pageSize.name)
  }

  /** 横向纵向(Landscape) */
  def orientation(orientation: Orientation): this.type = {
    set(GlobalSettings.Orientation, orientation.name)
  }

  /** 输出文档的颜色模式，Color/Grayscale */
  def colorMode(colorMode: String): this.type = {
    set(GlobalSettings.ColorMode, colorMode)
  }

  /** 文档的DPI */
  def dpi(dpi: Int): this.type = {
    set(GlobalSettings.Dpi, dpi)
  }

  /** 打印多份时，是否连续生成一份之后再生成下一份
   */
  def collate(collate: Boolean): this.type = {
    set(GlobalSettings.Collate, collate)
  }

  /** 是否生成文档大纲 */
  def outline(outline: Boolean): this.type = {
    set(GlobalSettings.Outline, outline)
  }

  /** 文档大纲的最大深度 */
  def outlineDepth(outlineDepth: Int): this.type = {
    set(GlobalSettings.OutlineDepth, outlineDepth)
  }

  /** 文档的标题 */
  def documentTitle(title: String): this.type = {
    set(GlobalSettings.DocumentTitle, title)
  }

  /** 是否启用PDF压缩 */
  def compression(compression: Boolean): this.type = {
    set(GlobalSettings.UseCompression, compression)
  }

  /** 边距(使用css单位，例如5in,15px),顺序按照 顶、右、底、左 */
  def margin(m: PageMargin): this.type = {
    set(GlobalSettings.MarginTop, m.top.inches())
    set(GlobalSettings.MarginRight, m.right.inches())
    set(GlobalSettings.MarginBottom, m.bottom.inches())
    set(GlobalSettings.MarginLeft, m.left.inches())
  }

  /** 图片的最大DPI */
  def imageDpi(imageDpi: Int): this.type = {
    set(GlobalSettings.ImageDpi, imageDpi)
  }

  /** 图片的压缩比(1-100) */
  def imageQuality(quality: Int): this.type = {
    set(GlobalSettings.ImageQuality, quality)
  }

  /** 当加载和存储cookie时使用的jar路径 */
  def cookieJar(cookieJar: String): this.type = {
    set(GlobalSettings.CookieJar, cookieJar)
  }

  /** 添加转换过程的监听器 */
  def progress(progressChangeConsumer: Consumer[ProgressPhase]): this.type = {
    progressCallbacks += progressChangeConsumer
    this
  }

  /** 添加一个成功转换后的可运行函数 */
  def success(successRunnable: Runnable): this.type = {
    finished((success: Boolean) => {
      if (success) {
        successRunnable.run()
      }
    })
  }

  /** 添加一个转换结束的可运行函数，成功与否都会调用 */
  def finished(finishConsumer: Consumer[Boolean]): this.type = {
    finishedCallbacks += finishConsumer
    this
  }

  /** 添加一个失败的回调函数 */
  def failure(failureRunnable: Runnable): this.type = {
    finished((success: Boolean) => {
      if (!success) {
        failureRunnable.run()
      }
    })
  }

  /** 添加一个页面 */
  def page(page: WKPage): this.type = {
    pages += page
    this
  }

  /** 执行转换，并将结果保存到指定路径
   *
   * @return true 如果转换成功否则，false
   */
  def saveAs(path: File): Boolean = {
    if (pages.isEmpty) {
      false
    } else {
      set(GlobalSettings.Out, path.getAbsolutePath)
      withConverter((p, library) => library.convert(p) == 1)
    }
  }

  /** 执行转换
   *
   * @return 转换后的输入流
   */
  def saveAs(): InputStream = {
    settings.remove(GlobalSettings.Out)
    withConverter((converter: Pointer, library: WKLibrary) => {
      val log = Collections.newBuffer[String]
      warning(w => log += ("Warning: " + w))
      error(e => log += ("Error: " + e))
      val out = new PointerByReference()
      if (library.convert(converter) == 1) {
        val size = library.wkhtmltopdf_get_output(converter, out)
        val pdfBytes = new Array[Byte](size.asInstanceOf[Int])
        out.getValue.read(0, pdfBytes, 0, pdfBytes.length)
        new ByteArrayInputStream(pdfBytes)
      } else {
        throw new ConvertException("Conversion returned with failure. Log:\n"
          + log.mkString("\n"))
      }
    })
  }

  /**
   * Adds a consumer for warning messages produced during the conversion process.
   */
  def warning(warningConsumer: Consumer[String]): this.type = {
    warningCallbacks += warningConsumer
    this
  }

  /** 添加一个监听转换过程中的失败消息的函数 */
  def error(errorConsumer: Consumer[String]): this.type = {
    errorCallbacks += errorConsumer
    this
  }

  /** 执行转换
   *
   * @param consumer 结果通知函数
   * @tparam T 结果类型
   * @return 结果
   */
  private def withConverter[T](consumer: (Pointer, WKLibrary) => T): T = {
    WKLibrary.withInstance { library =>
      val global = library.createGlobalSettings()
      settings.foreach { case (k, v) => library.setGlobal(global, k, v) }
      val converter = library.createConverter(global)
      library.setWarningCallback(converter, (_, s) => warningCallbacks.foreach(_.accept(s)))
      library.setErrorCallback(converter, (_, s) => errorCallbacks.foreach(_.accept(s)))
      library.setProgressChangedCallback(converter, (c, percent) => {
        progressCallbacks.foreach(_.accept(library.currentPhase(c, percent)))
      })
      library.setFinishedCallback(converter, (_, i) => finishedCallbacks.foreach(_.accept(i == 1)))
      val objectSettingList = Collections.newBuffer[Pointer]
      try {
        pages foreach { page =>
          val objectSettings = library.createObjectSettings()
          objectSettingList += objectSettings
          page.settings.foreach { case (k, v) => library.setObject(objectSettings, k, v) }
          library.addObject(converter, objectSettings, page.data)
        }
        consumer.apply(converter, library)
      } finally {
        library.destroy(global, objectSettingList, converter)
      }
    }
  }
}
