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

import org.apache.poi.xwpf.usermodel.*
import org.beangle.commons.io.IOs
import org.openxmlformats.schemas.wordprocessingml.x2006.main.{CTP, CTTbl}

import java.io.*
import java.net.URL
import java.util as ju
import scala.jdk.CollectionConverters.*

object DocMerger {

  /** 从 `url` 加载模板，在同一文档内复制共 `n` 份（份间插入分页符）；`n > 1` 时末尾写回内存再打开以同步 POI 缓存。
   *
   * @return 文档与各份正文在 `bodyElements` 中的半开区间 `[from, until)`
   */
  def copyN(url: URL, n: Int): (XWPFDocument, Seq[(Int, Int)]) = {
    require(n >= 1, s"n must be >= 1, got $n")
    val is = url.openStream()
    val bytes = try IOs.readBytes(is) finally is.close()
    if (n == 1) {
      val doc = new XWPFDocument(new ByteArrayInputStream(bytes))
      val bodySize = doc.getBodyElements.size()
      return (doc, Seq((0, bodySize)))
    }
    val target = new XWPFDocument(new ByteArrayInputStream(bytes))
    val source = new XWPFDocument(new ByteArrayInputStream(bytes))
    val templateBodySize = target.getBodyElements.size()
    val slotRanges = Seq.newBuilder[(Int, Int)]
    slotRanges += ((0, templateBodySize))
    try {
      var i = 1
      while (i < n) {
        target.getBodyElements.asScala.lastOption foreach { elem =>
          DocHelper.addPageBreakAfter(elem)
        }
        val appendAt = target.getBodyElements.size()
        copyTo(target, source)
        slotRanges += ((appendAt, appendAt + templateBodySize))
        i += 1
      }
      val bos = new ByteArrayOutputStream()
      target.write(bos)
      target.close()
      target == null //destroy,avoid close twice
      (new XWPFDocument(new ByteArrayInputStream(bos.toByteArray)), slotRanges.result())
    } finally {
      IOs.close(target, source)
    }
  }

  /** 按 OOXML 块级 CT 深拷贝正文（保留段落/表格版式）。 */
  private def copyTo(targetDoc: XWPFDocument, sourceDoc: XWPFDocument): Unit = {
    sourceDoc.getBodyElements.asScala.foreach { elem =>
      elem.getElementType match {
        case BodyElementType.PARAGRAPH =>
          val src = elem.asInstanceOf[XWPFParagraph]
          val dest = targetDoc.createParagraph()
          while (dest.getRuns.size() > 0) dest.removeRun(0)
          dest.getCTP.set(src.getCTP.copy().asInstanceOf[CTP])
        case BodyElementType.TABLE =>
          val src = elem.asInstanceOf[XWPFTable]
          val dest = targetDoc.createTable()
          while (dest.getRows.size() > 0) dest.removeRow(0)
          dest.getCTTbl.set(src.getCTTbl.copy().asInstanceOf[CTTbl])
        case _ =>
      }
    }
  }

  /** 将源文档的内容追加到目标文档末尾；图片在目标包内按字节去重（复用 drawing + rId，否则 `addPicture`）。 */
  def appendTo(targetDoc: XWPFDocument, sourceDoc: XWPFDocument): Unit = {
    val iterator = sourceDoc.getBodyElementsIterator
    while (iterator.hasNext) {
      iterator.next() match {
        case sourcePara: XWPFParagraph => copyParagraph(sourcePara, targetDoc.createParagraph())
        case sourceTable: XWPFTable => copyTable(sourceTable, targetDoc)
        case _ => // 忽略其他类型
      }
    }
  }

  /**
   * 复制段落（包括其中的图片）
   */
  private def copyParagraph(fromP: XWPFParagraph, toP: XWPFParagraph): Unit = {
    // 复制段落样式
    Option(fromP.getCTP.getPPr).foreach { ppr =>
      if (toP.getCTP.getPPr == null) {
        toP.getCTP.addNewPPr()
      }
      toP.getCTP.getPPr.set(ppr.copy())
    }

    // 复制每个 Run
    fromP.getRuns.forEach { sourceRun =>
      val newRun = toP.createRun()
      XwpfCopier.copyRunProps(sourceRun, newRun)
      Option(sourceRun.getText(0)).foreach(newRun.setText)

      val embedded = sourceRun.getEmbeddedPictures
      if embedded != null && !embedded.isEmpty then
        embedded.forEach { pic =>
          addPicture(newRun, sourceRun, pic)
        }
    }
  }

  private def findPictureData(doc: XWPFDocument, bytes: Array[Byte]): Option[XWPFPictureData] =
    doc.getAllPictures.asScala.find { pd =>
      val data = pd.getData
      data != null && data.length == bytes.length && ju.Arrays.equals(data, bytes)
    }

  private def addPicture(targetRun: XWPFRun, sourceRun: XWPFRun, picture: XWPFPicture): Unit = {
    try
      val pictureData = picture.getPictureData
      if pictureData == null then return
      val imageBytes = pictureData.getData
      val targetDoc = targetRun.getDocument

      findPictureData(targetDoc, imageBytes) match
        case Some(existing) =>
          val blipId = targetDoc.getRelationId(existing)
          if blipId == null || !XwpfDrawingCopy.copyPictureDrawing(sourceRun, targetRun, picture, blipId)
          then insertPicture(targetRun, picture, imageBytes)
        case None =>
          insertPicture(targetRun, picture, imageBytes)
    catch
      case e: Exception =>
        System.err.println(s"Failed to copy image: ${e.getMessage}")
  }

  private def insertPicture(targetRun: XWPFRun, picture: XWPFPicture, imageBytes: Array[Byte]): Unit = {
    val pictureData = picture.getPictureData
    val pictureType = pictureData.getPictureTypeEnum
    val widthEmu = picture.getCTPicture.getSpPr.getXfrm.getExt.getCx.toInt
    val heightEmu = picture.getCTPicture.getSpPr.getXfrm.getExt.getCy.toInt
    val mediaName =
      Option(pictureData.getFileName).map(_.trim).filter(_.nonEmpty).getOrElse("image.png")
    targetRun.addPicture(new ByteArrayInputStream(imageBytes), pictureType, mediaName, widthEmu, heightEmu)
  }

  /**
   * 复制表格
   */
  private def copyTable(sourceTable: XWPFTable, targetDoc: XWPFDocument): Unit = {
    val newTable = targetDoc.createTable()

    // 复制表格属性
    Option(sourceTable.getCTTbl.getTblPr).foreach { tblPr =>
      if (newTable.getCTTbl.getTblPr == null) {
        newTable.getCTTbl.addNewTblPr()
      }
      newTable.getCTTbl.getTblPr.set(tblPr.copy())
    }
    // 复制表结构
    //  createRow() 会沿用上一行的 w:tc 个数。这一行为在编辑中有用，但是在API对接中不太有用,所以我们要删掉多余的格子，增补缺少的格子
    for (rowIdx <- 0 until sourceTable.getRows.size()) {
      val sourceRow = sourceTable.getRow(rowIdx)
      val newRow = if (rowIdx == 0) newTable.getRow(0) else newTable.createRow()
      while (newRow.getTableCells.size() < sourceRow.getTableCells.size) {
        newRow.addNewTableCell()
      }
      while (newRow.getTableCells.size() > sourceRow.getTableCells.size) {
        newRow.removeCell(newRow.getTableCells.size() - 1)
      }
    }

    // 遍历行
    for (rowIdx <- 0 until sourceTable.getRows.size()) {
      val sourceRow = sourceTable.getRow(rowIdx)
      val newRow = newTable.getRow(rowIdx)
      //设置行属性
      XwpfCopier.copyRowProps(sourceRow, newRow)
      // 遍历单元格
      for (cellIdx <- 0 until sourceRow.getTableCells.size()) {
        val sourceCell = sourceRow.getCell(cellIdx)
        val newCell = newRow.getCell(cellIdx)
        XwpfCopier.copyCellProps(sourceCell, newCell)
        val srcParas = sourceCell.getParagraphs
        // 复制单元格内的段落
        (0 until srcParas.size) foreach { i =>
          val sourcePara = srcParas.get(i)
          val targetPara = if (i >= newCell.getParagraphs.size) newCell.addParagraph() else newCell.getParagraphs.get(i)
          copyParagraph(sourcePara, targetPara)
        }
      }
    }
  }

  /** 将 `second` 的正文（含图片）追加到 `first` 末尾，写入 `output`。 */
  def merge(first: File, second: File, output: File): Unit = {
    val target = new XWPFDocument(new FileInputStream(first))
    try {
      val source = new XWPFDocument(new FileInputStream(second))
      try {
        appendTo(target, source)
      } finally {
        source.close()
      }
      val parent = output.getParentFile
      if parent != null && !parent.exists() then parent.mkdirs()
      val fos = new FileOutputStream(output)
      try target.write(fos)
      finally fos.close()
    } finally {
      target.close()
    }
  }

  /** 命令行试合并：`DocMerge doc1.docx doc2.docx [out.docx]` */
  def main(args: Array[String]): Unit = {
    if args.length < 2 then
      println("Usage: org.beangle.doc.docx.DocMerge <first.docx> <second.docx> [output.docx]")
      println("  first  作为合并基底；second 追加到其末尾")
      println("  output 省略时写入 <first 同目录>/<first 文件名>_merged.docx")
      return

    val first = new File(args(0))
    val second = new File(args(1))
    if !first.isFile then throw new IllegalArgumentException(s"file not found: ${first.getAbsolutePath}")
    if !second.isFile then throw new IllegalArgumentException(s"file not found: ${second.getAbsolutePath}")
    val output = {
      if args.length >= 3 then new File(args(2))
      else {
        val base = first.getName
        val dot = base.lastIndexOf('.')
        val stem = if dot > 0 then base.substring(0, dot) else base
        new File(first.getParentFile, s"${stem}_merged.docx")
      }
    }
    merge(first, second, output)
    println(s"merged -> ${output.getAbsolutePath}")
  }

}
