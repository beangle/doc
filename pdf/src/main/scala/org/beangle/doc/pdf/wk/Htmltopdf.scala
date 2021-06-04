/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.doc.pdf.wk


import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import org.beangle.commons.collection.Collections
import org.beangle.doc.core.{ConvertException, Orientations, PageSizes, ProgressPhase}

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
 *
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
    this.settings ++= initSettings
  }

  def getSetting(name: String): Option[String] = {
    settings.get(name)
  }

  /** 禁止只能缩小策略(WebKit会依据pixel/dpi比例) */
  def disableSmartShrinking(disable: Boolean): this.type = {
    set("disable-smart-shrinking", disable)
  }

  /** 纸张大小(A3,A4,A5..) */
  def pageSize(pageSize: PageSizes.PageSize): this.type = {
    set("size.pageSize", pageSize.name)
  }

  def set(name: String, value: Any): this.type = {
    value match {
      case None => this.settings.remove(name)
      case null => this.settings.remove(name)
      case _ => this.settings.put(name, value.toString)
    }
    this
  }

  /** 横向纵向(Landscape) */
  def orientation(orientation: Orientations.Orientation): this.type = {
    set("orientation", orientation.name)
  }

  /** 输出文档的颜色模式，Color/Grayscale */
  def colorMode(colorMode: String): this.type = {
    set("colorMode", colorMode)
  }

  /** 文档的DPI */
  def dpi(dpi: Int): this.type = {
    set("dpi", dpi)
  }

  /** 打印多份时，是否连续生成一份之后再生成下一份
   * Whether or not to collate copies.
   */
  def collate(collate: Boolean): this.type = {
    set("collate", collate)
  }

  /** 是否生成文档大纲 */
  def outline(outline: Boolean): this.type = {
    set("outline", outline)
  }

  /** 文档大纲的最大深度 */
  def outlineDepth(outlineDepth: Int): this.type = {
    set("outlineDepth", outlineDepth)
  }

  /** 文档的标题 */
  def documentTitle(title: String): this.type = {
    set("documentTitle", title)
  }

  /** 是否启用PDF压缩 */
  def compression(compression: Boolean): this.type = {
    set("useCompression", compression)
  }

  /** 边距(使用css单位，例如5in,15px),顺序按照 顶、右、底、左 */
  def margin(marginTop: String, marginRight: String, marginBottom: String, marginLeft: String): this.type = {
    set("margin.top", marginTop)
    set("margin.right", marginRight)
    set("margin.bottom", marginBottom)
    set("margin.left", marginLeft)
  }

  /** 图片的最大DPI */
  def imageDpi(imageDpi: Int): this.type = {
    set("imageDPI", imageDpi)
  }

  /** 图片的压缩比(1-100) */
  def imageQuality(quality: Int): this.type = {
    set("imageQuality", quality)
  }

  /** 当加载和存储cookie时使用的jar路径 */
  def cookieJar(cookieJar: String): this.type = {
    set("load.cookieJar", cookieJar)
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
      set("out", path.getAbsolutePath)
      withConverter((p, library) => library.wkhtmltopdf_convert(p) == 1)
    }
  }

  /** 执行转换
   *
   * @return 转换后的输入流
   */
  def saveAs(): InputStream = {
    settings.remove("out")
    withConverter((point: Pointer, library: WKLibrary) => {
      val log = Collections.newBuffer[String]
      warning(w => log += ("Warning: " + w))
      error(e => log += ("Error: " + e))
      val out = new PointerByReference()
      if (library.wkhtmltopdf_convert(point) == 1) {
        val size = library.wkhtmltopdf_get_output(point, out)
        val pdfBytes = new Array[Byte](size.asInstanceOf[Int])
        out.getValue.read(0, pdfBytes, 0, pdfBytes.length)
        return new ByteArrayInputStream(pdfBytes)
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
      val globalSettings = library.wkhtmltopdf_create_global_settings()
      settings.foreach { case (k, v) => library.wkhtmltopdf_set_global_setting(globalSettings, k, v) }
      val converter = library.wkhtmltopdf_create_converter(globalSettings)
      library.wkhtmltopdf_set_warning_callback(converter, (_, s) => warningCallbacks.foreach(_.accept(s)))
      library.wkhtmltopdf_set_error_callback(converter, (_, s) => errorCallbacks.foreach(_.accept(s)))
      library.wkhtmltopdf_set_progress_changed_callback(converter, (c, phaseProgress) => {
        val phase = library.wkhtmltopdf_current_phase(c)
        val totalPhases = library.wkhtmltopdf_phase_count(c)
        val phaseDesc = library.wkhtmltopdf_phase_description(c, phase)
        val progress = ProgressPhase(phase, phaseDesc, totalPhases, phaseProgress)
        progressCallbacks.foreach(_.accept(progress))
      })
      library.wkhtmltopdf_set_finished_callback(converter, (_, i) => finishedCallbacks.foreach(_.accept(i == 1)))
      try {
        pages foreach { page =>
          val objectSettings = library.wkhtmltopdf_create_object_settings()
          page.settings.foreach { case (k, v) => library.wkhtmltopdf_set_object_setting(objectSettings, k, v) }
          library.wkhtmltopdf_add_object(converter, objectSettings, page.data)
        }
        consumer.apply(converter, library)
      } finally {
        library.wkhtmltopdf_destroy_converter(converter)
      }
    }
  }
}
