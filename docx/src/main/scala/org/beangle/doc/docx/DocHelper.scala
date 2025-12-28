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

package org.beangle.doc.docx

import org.apache.poi.common.usermodel.PictureType
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.beangle.commons.lang.Strings

import java.net.URL

object DocHelper {

  @deprecated("using DocTemplate")
  def toDoc(url: URL, data: collection.Map[String, Any]): Array[Byte] = {
    DocTemplate.process(url, data)
  }

  /** 读取一个run的文本
   *
   * @param run
   * @return
   */
  def readText(run: XWPFRun): String = {
    val ctr = run.getCTR
    val size = run.getCTR.sizeOfTArray
    size match
      case 0 => ""
      case 1 => ctr.getTArray(0).getStringValue
      case _ =>
        val sb = new StringBuilder()
        (0 until size) foreach { i =>
          val t = ctr.getTArray(i).getStringValue
          if Strings.isNotEmpty(t) then
            sb.addAll(t)
        }
        sb.mkString
  }

  def set(run: XWPFRun, text: String): Unit = {
    run.setText(text, 0)
  }

  def set(run: XWPFRun, components: Iterable[Any]): Unit = {
    val ctr = run.getCTR
    var size = run.getCTR.sizeOfTArray
    while (size > 0) {
      ctr.removeT(size - 1)
      size -= 1
    }
    components foreach {
      case s: String =>
        if s == "\t" then run.addTab()
        else run.setText(s)
      case p: Picture =>
        val pictureType = PictureType.valueOf(p.mediaType.subType.toUpperCase)
        run.addPicture(p.is, pictureType, p.filename, p.width, p.height)
    }

  }
}
