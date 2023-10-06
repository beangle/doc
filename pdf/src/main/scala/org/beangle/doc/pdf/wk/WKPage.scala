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

import org.beangle.commons.collection.Collections
import org.beangle.doc.core.ErrorPolicy
import org.beangle.doc.pdf.wk.ObjectSettings._

object WKPage {
  /** 使用HTML文本创建页面 */
  def html(html: String): WKPage = {
    if (html == null || html.isEmpty || html.startsWith("\u0000")) {
      throw new IllegalArgumentException("No content specified for object.")
    }
    new WKPage(html)
  }

  /** 使用URL网址或者本地文件地址创建对象 */
  def url(url: String): WKPage = {
    new WKPage(null, Map(Page -> url))
  }
}

class WKPage(val data: String) {

  private val settingMap = Collections.newMap[String, String]

  def settings: Map[String, String] = {
    settingMap.toMap
  }

  def this(data: String, initSetting: Map[String, String]) = {
    this(data)
    initSetting foreach { case (k, v) =>
      if (isValid(k)) settingMap.put(k, v)
    }
  }

  def set(name: String, value: Any): this.type = {
    if (isValid(name)) {
      value match {
        case None => this.settingMap.remove(name)
        case null => this.settingMap.remove(name)
        case _ => this.settingMap.put(name, value.toString)
      }
    } else {
      throw new RuntimeException(s"Cannot recognize object settings $name")
    }
    this
  }

  /** 是否显示背景(缺省是) */
  def showBackground(background: Boolean): this.type = {
    set(Background, background)
  }

  /** 是否加载图片（缺省是) */
  def loadImages(load: Boolean): this.type = {
    set(LoadImages, load)
  }

  /** 是否启用Javascript（缺省是) */
  def enableJavascript(enable: Boolean): this.type = {
    set(EnableJavascript, enable)
  }

  /** 是否启用智能缩减（缺省是) */
  def enableIntelligentShrinking(enable: Boolean): this.type = {
    set(EnableIntelligentShrinking, enable)
  }

  /** 最小字体 */
  def minimumFontSize(size: Int): this.type = {
    set(MminimumFontSize, size)
  }

  /** 是否设置为打印介质(缺省否) */
  def usePrintMediaType(use: Boolean): this.type = {
    set(UsePrintMediaType, use)
  }

  /** 当网页没有指定编码时的缺省编码 */
  def defaultEncoding(encoding: String): this.type = {
    set(DefaultEncoding, encoding)
  }

  /** CSS样式单的url或者路径 */
  def userStyleSheet(urlOrPath: String): this.type = {
    set(UserStyleSheet, urlOrPath)
  }

  /** 请求网页需要认证时的用户名 */
  def authUsername(username: String): this.type = {
    set(AuthUsername, username)
  }

  /** 请求网页需要认证时的密码 */
  def authPassword(password: String): this.type = {
    set(AuthPassword, password)
  }

  /** 在加载页面到转换pdf之间，留给Javascript的延迟时间. */
  def javascriptDelay(delayMs: Int): this.type = {
    set(JavascriptDelay, delayMs)
  }

  /** 缩放比例，默认1 */
  def zoomFactor(factor: Float): this.type = {
    set(ZoomFactor, factor)
  }

  /** 是否禁止从本地加载资源 */
  def blockLocalFileAccess(block: Boolean): this.type = {
    set(BlockLocalFileAccess, block)
  }

  /** 是否停止缓慢的javascript */
  def stopSlowScript(stop: Boolean): this.type = {
    set(StopSlowScript, stop)
  }

  /** 是否调试javascript */
  def debugJavascrip(debug: Boolean): this.type = {
    set(DebugJavascript, debug)
  }

  /** 对于一个网页加载出错的处理办法 */
  def handleErrors(errorHandling: ErrorPolicy): this.type = {
    set(LoadErrorHandling, errorHandling.name)
  }

  /** 自定义头的页眉字体 */
  def headerFont(fontName: String, size: Int): this.type = {
    set(HeaderFontName, fontName)
    set(HeaderFontSize, size)
  }

  /** 是否在自定义页眉下添加一条线 */
  def headerLine(line: Boolean): this.type = {
    set(HeaderLine, line)
  }

  /** 页眉和内容之间空格的数量 */
  def headerSpacing(spacing: Int): this.type = {
    set(HeaderSpacing, spacing)
  }

  /** 页眉的URL */
  def headerHtmlUrl(url: String): this.type = {
    set(HeaderHtmlUrl, url)
  }

  /** 页眉左边的文本 */
  def header(left: String, center: String, right: String): this.type = {
    set(HeaderLeft, left)
    set(HeaderCenter, center)
    set(HeaderRight, right)
  }

  /** 是否在生成的目录中使用点线 */
  def tocUseDottedLines(dottedLines: Boolean): this.type = {
    set(TocUseDottedLines, dottedLines)
  }

  /** 目录名称 */
  def tocCaptionText(captionText: String): this.type = {
    set(TocCaptionText, captionText)
  }

  /** 是否在目录中使用链接，以方便浏览 */
  def tocForwardLinks(forward: Boolean): this.type = {
    set(TocForwardLinks, forward)
  }

  /** 是否允许内容反向链接到目录 */
  def tocBackLinks(backLinks: Boolean): this.type = {
    set(TocBackLinks, backLinks)
  }

  /** 目录内的缩进尺度，使用CSS中允许的单位(例如"5px", "2em".) */
  def tocIndentation(indentation: String): this.type = {
    set(TocIndentation, indentation)
  }

  /** 缩进缩小的比例，例如0.8表示缩小20% */
  def tocFontScale(scale: Float): this.type = {
    set(TocFontScale, scale)
  }

  /** 是否将文档中的章节也放入到目录的大纲里 */
  def tocIncludeSections(include: Boolean): this.type = {
    set(TocIncludeSections, include)
  }

  /** 是否保持外部超链接 */
  def useExternalLinks(use: Boolean): this.type = {
    set(UseExternalLinks, use)
  }

  /** 是否将内部链接转为pdf内部引用 */
  def convertInternalLinksToPdfReferences(convert: Boolean): this.type = {
    set(UseLocalLinks, convert)
  }

  /** 是否将HTML表单转换位PDF表单 */
  def produceForms(produce: Boolean): this.type = {
    set(ProduceForms, produce)
  }

  /** 是否将页码放入到页眉，页脚和目录中 */
  def pageCount(pageCount: Boolean): this.type = {
    set(PagesCount, pageCount)
  }

}
