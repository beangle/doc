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

object GlobalSettings {

  val DisableSmartShrinking = "disable-smart-shrinking"
  val Orientation = "orientation"
  val PageSize = "size.pageSize"
  val ColorMode = "colorMode"
  val Dpi = "dpi"
  val Collate = "collate"
  val Outline = "outline"
  val OutlineDepth = "outlineDepth"
  val DocumentTitle = "documentTitle"
  val UseCompression = "useCompression"

  val MarginTop = "margin.top"
  val MarginRight = "margin.right"
  val MarginBottom = "margin.bottom"
  val MarginLeft = "margin.left"

  val ImageDpi = "imageDPI"
  val ImageQuality = "imageQuality"
  val CookieJar = "load.cookieJar"

  def isValid(name: String): Boolean = {
    SettingNames.contains(name)
  }

  private val SettingNames = Set(DisableSmartShrinking, Orientation, ColorMode, Dpi, Collate,
    Outline, OutlineDepth, DocumentTitle, UseCompression, MarginTop, MarginRight, MarginBottom, MarginLeft,
    ImageDpi, ImageQuality,CookieJar)
}
