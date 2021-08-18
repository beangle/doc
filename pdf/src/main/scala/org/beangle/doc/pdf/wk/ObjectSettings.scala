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

object ObjectSettings {

  def isValid(name: String): Boolean = {
    SettingNames.contains(name)
  }

  val Background = "web.background"
  val LoadImages = "web.loadImages"
  val EnableJavascript = "web.enableJavascript"
  val EnableIntelligentShrinking = "web.enableIntelligentShrinking"
  val MminimumFontSize = "web.minimumFontSize"
  val UsePrintMediaType = "web.printMediaType"
  val DefaultEncoding = "web.defaultEncoding"
  val UserStyleSheet = "web.userStyleSheet"

  val AuthUsername = "load.username"
  val AuthPassword = "load.password"
  val JavascriptDelay = "load.jsdelay"
  val ZoomFactor = "load.zoomFactor"
  val BlockLocalFileAccess = "load.blockLocalFileAccess"
  val StopSlowScript = "load.stopSlowScript"
  val DebugJavascript = "load.debugJavascript"
  val LoadErrorHandling = "load.loadErrorHandling"

  val HeaderFontSize = "header.fontSize"
  val HeaderFontName = "header.fontName"
  val HeaderLine = "header.line"
  val HeaderSpacing = "header.spacing"
  val HeaderHtmlUrl = "header.htmlUrl"
  var HeaderLeft = "header.left"
  val HeaderCenter = "header.center"
  val HeaderRight = "header.right"

  val TocUseDottedLines = "toc.useDottedLines"
  val TocCaptionText = "toc.captionText"
  val TocForwardLinks = "toc.forwardLinks"
  val TocBackLinks = "toc.backLinks"
  val TocIndentation = "toc.indentation"
  val TocFontScale = "toc.fontScale"
  val TocIncludeSections = "includeInOutline"

  val UseExternalLinks = "useExternalLinks"
  val UseLocalLinks = "useLocalLinks"
  val ProduceForms = "produceForms"
  val PagesCount = "pagesCount"
  val Page = "page"

  private val SettingNames = Set(Background, LoadImages, EnableJavascript, EnableIntelligentShrinking,
    MminimumFontSize, UsePrintMediaType, DefaultEncoding, UserStyleSheet, AuthUsername, AuthPassword,
    JavascriptDelay, ZoomFactor, BlockLocalFileAccess, StopSlowScript, DebugJavascript, LoadErrorHandling,

    HeaderFontSize, HeaderFontName, HeaderLine, HeaderSpacing, HeaderHtmlUrl, HeaderLeft,
    HeaderCenter, HeaderRight,

    TocUseDottedLines, TocCaptionText, TocForwardLinks, TocBackLinks, TocIndentation, TocFontScale, TocIncludeSections,

    UseExternalLinks, UseLocalLinks, ProduceForms, PagesCount, Page)
}
