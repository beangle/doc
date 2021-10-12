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

package org.beangle.doc.excel.template.directive

import org.beangle.commons.io.IOs
import org.beangle.doc.excel.*
import org.beangle.doc.excel.template.*

import java.io.InputStream

class ImageDirective(src: String, imageTypeStr: String, area: Area) extends AbstractDirective {
  var scaleX: Option[Double] = None
  var scaleY: Option[Double] = None
  super.addArea(area)

  private def needResizePicture = this.scaleX.nonEmpty && this.scaleY.nonEmpty

  def getLockRange: Boolean = if (needResizePicture) false else lockRange

  override def applyAt(cellRef: CellRef, context: Context): Size = {
    val area = areas.head
    val imageAnchorAreaSize = new Size(area.size.width + 1, area.size.height + 1)
    val imageAnchorArea = AreaRef(cellRef, imageAnchorAreaSize)
    val imgObj = context.evaluator.eval(src, context.toMap)
    if (imgObj == null) return area.size
    val imgBytes = imgObj match {
      case ba: Array[Byte] => ba
      case is: InputStream => IOs.readBytes(is)
      case _ => throw new IllegalArgumentException("src value must contain image bytes (byte[])")
    }
    val wb = area.transformer.workbook
    Workbooks.addImage(wb, imageAnchorArea, imgBytes, ImageType.valueOf(imageTypeStr), scaleX, scaleY)
    area.size
  }
}
