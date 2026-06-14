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

import org.apache.poi.ooxml.POIXMLTypeLoader.DEFAULT_XML_OPTIONS
import org.apache.poi.xwpf.usermodel.{XWPFPicture, XWPFRun}
import org.apache.xmlbeans.XmlObject
import org.openxmlformats.schemas.drawingml.x2006.main.CTBlip
import org.openxmlformats.schemas.drawingml.x2006.picture.CTPicture
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.{CTAnchor, CTInline}
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDrawing

/** 深拷贝 `w:drawing`，复用目标包内已有 media 的 relationship（改写 `a:blip@r:embed`）。 */
private[docx] object XwpfDrawingCopy {

  private val NsDrawingMain = "http://schemas.openxmlformats.org/drawingml/2006/main"
  private val BlipPath = s"declare namespace a='$NsDrawingMain' .//a:blip"

  /** @return 是否已成功把 drawing 挂到 `targetRun` */
  def copyPictureDrawing(sourceRun: XWPFRun, targetRun: XWPFRun, picture: XWPFPicture, targetBlipId: String): Boolean = {
    findSourceDrawing(sourceRun, picture) match
      case None => false
      case Some(sourceDrawing) =>
        val cloned = cloneDrawing(sourceDrawing)
        setAllBlipEmbeds(cloned, targetBlipId)
        reassignDrawingIds(cloned)
        targetRun.getCTR.addNewDrawing().set(cloned)
        true
  }

  private def cloneDrawing(source: CTDrawing): CTDrawing = {
    CTDrawing.Factory.parse(source.xmlText(), DEFAULT_XML_OPTIONS)
  }

  private def findSourceDrawing(run: XWPFRun, picture: XWPFPicture): Option[CTDrawing] = {
    val ctr = run.getCTR
    if ctr == null then None
    else
      val drawings = ctr.getDrawingArray
      if drawings == null || drawings.isEmpty then None
      else
        blipEmbedId(picture.getCTPicture).flatMap { embed =>
          drawings.find(d => drawingContainsBlip(d, embed))
        }
  }

  private def blipEmbedId(pic: CTPicture): Option[String] =
    Option(pic.getBlipFill).flatMap(b => Option(b.getBlip)).map(_.getEmbed).filter(id => id != null && id.nonEmpty)

  private def drawingContainsBlip(drawing: CTDrawing, embedId: String): Boolean =
    drawing.selectPath(BlipPath) exists {
      case b: CTBlip => embedId == b.getEmbed
      case o => blipEmbed(o).contains(embedId)
    }

  private def setAllBlipEmbeds(drawing: CTDrawing, blipId: String): Unit =
    Option(drawing.selectPath(BlipPath)).foreach(_.foreach {
      case b: CTBlip => b.setEmbed(blipId)
      case other =>
        val b = CTBlip.Factory.parse(other.toString)
        b.setEmbed(blipId)
        other.set(b)
    })

  private def blipEmbed(o: XmlObject): Option[String] = {
    o match
      case b: CTBlip => Option(b.getEmbed).filter(_.nonEmpty)
      case other => Option(CTBlip.Factory.parse(other.toString).getEmbed).filter(_.nonEmpty)
  }

  private def reassignDrawingIds(drawing: CTDrawing): Unit = {
    var i = 0
    while i < drawing.sizeOfInlineArray do {
      reassignDocPr(drawing.getInlineArray(i))
      i += 1
    }
    i = 0
    while i < drawing.sizeOfAnchorArray do {
      reassignDocPr(drawing.getAnchorArray(i))
      i += 1
    }
  }

  private def reassignDocPr(inline: CTInline): Unit = {
    val docPr = inline.getDocPr
    if docPr != null then
      val id = reserveDrawingId()
      docPr.setId(id)
      docPr.setName(s"Drawing $id")
  }

  private def reassignDocPr(anchor: CTAnchor): Unit ={
    val docPr = anchor.getDocPr
    if docPr != null then
      val id = reserveDrawingId()
      docPr.setId(id)
      docPr.setName(s"Drawing $id")
  }

  private def reserveDrawingId(): Long = System.nanoTime() & 0x7ffffffL
}
