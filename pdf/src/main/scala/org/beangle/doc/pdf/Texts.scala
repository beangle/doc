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

package org.beangle.doc.pdf

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.canvas.parser.data.{IEventData, TextRenderInfo}
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy
import com.itextpdf.kernel.pdf.canvas.parser.{EventType, PdfTextExtractor}
import org.beangle.commons.collection.Collections

object Texts {

  def getLocations(doc: PdfDocument, text: String): Iterable[(Float, Float)] = {
    val strategy = new CustomLocationTextExtractionStrategy(text)
    PdfTextExtractor.getTextFromPage(doc.getPage(1), strategy)
    strategy.locations
  }

  class CustomLocationTextExtractionStrategy(searchString: String) extends LocationTextExtractionStrategy {
    val locations = Collections.newBuffer[(Float, Float)]
    var index = 0
    var loc: com.itextpdf.kernel.geom.Vector = _

    override def eventOccurred(data: IEventData, `type`: EventType): Unit = {
      super.eventOccurred(data, `type`)
      // 捕获文本渲染事件，提取TextChunk
      if (`type` eq EventType.RENDER_TEXT) {
        val renderInfo = data.asInstanceOf[TextRenderInfo]
        val text = renderInfo.getText

        if (text != null) {
          if (text.contains(searchString)) {
            val loc = renderInfo.getBaseline.getStartPoint
            locations.addOne((loc.get(0), loc.get(1)))
            index = 0
          } else {
            if (searchString.indexOf(text) == index) {
              if (index == 0) {
                loc = renderInfo.getBaseline.getStartPoint
              }
              index += text.length
              if (index == searchString.length) {
                locations.addOne((loc.get(0), loc.get(1)))
                index = 0
              }
            } else {
              index = 0
            }
          }
        }
      }
    }
  }
}
