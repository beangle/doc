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

import org.beangle.commons.collection.Collections
import org.beangle.doc.core.ErrorPolicies

import scala.jdk.javaapi.CollectionConverters

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
    new WKPage(null, Map("page" -> url))
  }
}

class WKPage(val data: String) {

  val settings: collection.mutable.Map[String, String] = Collections.newMap[String, String]

  def getSettings: java.util.Map[String, String] = {
    CollectionConverters.asJava(settings)
  }

  def this(data: String, initSetting: Map[String, String]) {
    this(data)
    settings ++= initSetting
  }

  /** 是否显示背景(缺省是) */
  def showBackground(background: Boolean): this.type = {
    set("web.background", background)
  }

  /** 是否加载图片（缺省是) */
  def loadImages(load: Boolean): this.type = {
    set("web.loadImages", load)
  }

  /** 是否启用Javascript（缺省是) */
  def enableJavascript(enable: Boolean): this.type = {
    set("web.enableJavascript", enable)
  }

  /** 是否启用只能缩减（缺省是) */
  def enableIntelligentShrinking(enable: Boolean): this.type = {
    set("web.enableIntelligentShrinking", enable)
  }

  /** 最小字体 */
  def minimumFontSize(size: Int): this.type = {
    set("web.minimumFontSize", size)
  }

  /** 是否设置位打印介质(缺省否) */
  def usePrintMediaType(use: Boolean): this.type = {
    set("web.printMediaType", use)
  }

  /** 当网页没有指定编码时的缺省编码 */
  def defaultEncoding(encoding: String): this.type = {
    set("web.defaultEncoding", encoding)
  }

  /** CSS样式单的url或者路径 */
  def userStylesheet(urlOrPath: String): this.type = {
    set("web.userStyleSheet", urlOrPath)
  }

  /** 请求网页需要认证时的用户名 */
  def authUsername(username: String): this.type = {
    set("load.username", username)
  }

  /** 请求网页需要认证时的密码 */
  def authPassword(password: String): this.type = {
    set("load.password", password)
  }

  /** 在加载页面到转换pdf之间，留给Javascript的延迟时间. */
  def javascriptDelay(delayMs: Int): this.type = {
    set("load.jsdelay", delayMs)
  }

  /** 缩放比例，默认1 */
  def zoomFactor(factor: Float): this.type = {
    set("load.zoomFactor", factor)
  }

  /** 是否禁止从本地加载资源 */
  def blockLocalFileAccess(block: Boolean): this.type = {
    set("load.blockLocalFileAccess", block)
  }

  /** 是否停止缓慢的javascript */
  def stopSlowScript(stop: Boolean): this.type = {
    set("load.stopSlowScript", stop)
  }

  /** 是否调试javascript */
  def debugJavascriptWarningsAndErrors(debug: Boolean): this.type = {
    set("load.debugJavascript", debug)
  }

  private def set(name: String, value: Any): this.type = {
    value match {
      case None => this.settings.remove(name)
      case null => this.settings.remove(name)
      case _ => this.settings.put(name, value.toString)
    }
    this
  }

  /** 对于一个网页加载出错的处理办法 */
  def handleErrors(errorHandling: ErrorPolicies.Policy): this.type = {
    set("load.loadErrorHandling", errorHandling.name)
  }

  /** 自定义头的页眉字体 */
  def headerFontSize(size: Int): this.type = {
    set("header.fontSize", size)
  }

  /** 页眉自定义字体名称 */
  def headerFontName(fontName: String): this.type = {
    set("header.fontName", fontName)
  }

  /** 是否在自定义页眉下添加一条线 */
  def headerLine(line: Boolean): this.type = {
    set("header.line", line)
  }

  /** 页眉和内容之间空格的数量 */
  def headerSpacing(spacing: Int): this.type = {
    set("header.spacing", spacing)
  }

  /** 页眉的URL */
  def headerHtmlUrl(url: String): this.type = {
    set("header.htmlUrl", url)
  }

  /** 页眉左边的文本 */
  def headerLeft(text: String): this.type = {
    set("header.left", text)
  }

  /** 页眉中间的文本 */
  def headerCenter(text: String): this.type = {
    set("header.center", text)
  }

  /** 页眉右边的文本 */
  def headerRight(text: String): this.type = {
    set("header.right", text)
  }

  /** 是否在生成的目录中使用点线 */
  def tableOfContentsDottedLines(dottedLines: Boolean): this.type = {
    set("toc.useDottedLines", dottedLines)
  }

  /** 目录名称 */
  def tableOfContentsCaptionText(captionText: String): this.type = {
    set("toc.captionText", captionText)
  }

  /** 是否在目录中使用链接，以方便浏览 */
  def tableOfContentsForwardLinks(forward: Boolean): this.type = {
    set("toc.forwardLinks", forward)
  }

  /** 是否允许内容反向链接到目录 */
  def tableOfContentsBackLinks(backLinks: Boolean): this.type = {
    set("toc.backLinks", backLinks)
  }

  /** 目录内的缩进尺度，使用CSS中允许的单位(例如"5px", "2em".) */
  def tableOfContentsIndentation(indentation: String): this.type = {
    set("toc.indentation", indentation)
  }

  /** 缩进缩小的比例，例如0.8表示缩小20% */
  def tableOfContentsIndentationFontScaleDown(scale: Float): this.type = {
    set("toc.fontScale", scale)
  }

  /** 是否将文档中的章节也放入到目录的大纲里 */
  def tableOfContentsIncludeSections(include: Boolean): this.type = {
    set("includeInOutline", include)
  }

  /** 是否保持外部超链接 */
  def useExternalLinks(use: Boolean): this.type = {
    set("useExternalLinks", use)
  }

  /** 是否将内部链接转为pdf内部引用 */
  def convertInternalLinksToPdfReferences(convert: Boolean): this.type = {
    set("useLocalLinks", convert)
  }

  /** 是否将HTML表单转换位PDF表单 */
  def produceForms(produce: Boolean): this.type = {
    set("produceForms", produce)
  }

  /** 是否将页码放入到页眉，页脚和目录中 */
  def pageCount(pageCount: Boolean): this.type = {
    set("pagesCount", pageCount)
  }

}