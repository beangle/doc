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

import org.apache.poi.xwpf.usermodel.{XWPFRun, XWPFTableCell, XWPFTableRow}
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*

object XwpfCopier {

  /** 复制 run 的 `w:rPr`（删除线、底纹/背景色、上下标等），避免逐个 API 遗漏。 */
  def copyRunProps(src: XWPFRun, dest: XWPFRun): Unit = {
    Option(src.getCTR.getRPr).foreach { srcRPr =>
      val destCtr = dest.getCTR
      val destRPr = if destCtr.isSetRPr then destCtr.getRPr else destCtr.addNewRPr()
      destRPr.set(srcRPr.copy())
    }
  }

  def copyRowProps(src: XWPFTableRow, dst: XWPFTableRow): Unit = {
    //设置行属性
    Option(src.getCtRow.getTrPr).foreach { srcTrPr =>
      val destTr = dst.getCtRow
      val destTrPr = if destTr.isSetTrPr then destTr.getTrPr else destTr.addNewTrPr()
      destTrPr.set(srcTrPr.copy())
    }
  }

  /**
   * 完整复制单元格的所有属性
   */
  def copyCellProps(src: XWPFTableCell, dst: XWPFTableCell): Unit = {
    val sourceTcPr = src.getCTTc.getTcPr
    if (sourceTcPr == null) return

    // 获取或创建目标单元格的 TcPr
    val newTcPr = Option(dst.getCTTc.getTcPr).getOrElse(dst.getCTTc.addNewTcPr())

    // 1. 跨行合并属性 (vMerge)
    Option(sourceTcPr.getVMerge).foreach { sourceVMerge =>
      val newVMerge = CTVMerge.Factory.newInstance()
      newVMerge.setVal(sourceVMerge.getVal)
      newTcPr.setVMerge(newVMerge)
    }

    // 2. 跨列合并属性 - hMerge 方式
    Option(sourceTcPr.getHMerge).foreach { sourceHMerge =>
      val newHMerge = CTHMerge.Factory.newInstance()
      newHMerge.setVal(sourceHMerge.getVal)
      newTcPr.setHMerge(newHMerge)
    }

    // 3. 跨列合并属性 - gridSpan 方式（Word 2010+ 常用）
    Option(sourceTcPr.getGridSpan).foreach { sourceGridSpan =>
      val newGridSpan = CTDecimalNumber.Factory.newInstance()
      newGridSpan.setVal(sourceGridSpan.getVal)
      newTcPr.setGridSpan(newGridSpan)
    }

    // 4. 单元格宽度 (tcW)
    Option(sourceTcPr.getTcW).foreach { sourceTcW =>
      val newTcW = if (newTcPr.getTcW == null) newTcPr.addNewTcW() else newTcPr.getTcW
      newTcW.setType(sourceTcW.getType)
      newTcW.setW(sourceTcW.getW)
    }

    // 5. 垂直对齐方式 (vAlign)
    Option(sourceTcPr.getVAlign).foreach { sourceVAlign =>
      val newVAlign = if (newTcPr.getVAlign == null) newTcPr.addNewVAlign() else newTcPr.getVAlign
      newVAlign.setVal(sourceVAlign.getVal)
    }

    // 6. 单元格边框 (tcBorders)
    Option(sourceTcPr.getTcBorders).foreach { sourceBorders =>
      val newBorders = if (newTcPr.getTcBorders == null) newTcPr.addNewTcBorders() else newTcPr.getTcBorders
      copyCellBorders(sourceBorders, newBorders)
    }

    // 7. 单元格背景色/底纹 (shd)
    Option(sourceTcPr.getShd).foreach { sourceShd =>
      val newShd = if (newTcPr.getShd == null) newTcPr.addNewShd() else newTcPr.getShd
      newShd.setVal(sourceShd.getVal)
      newShd.setColor(sourceShd.getColor)
      newShd.setFill(sourceShd.getFill)
    }

    // 8. 单元格内边距 (tcMar)
    Option(sourceTcPr.getTcMar).foreach { sourceMar =>
      val newMar = if (newTcPr.getTcMar == null) newTcPr.addNewTcMar() else newTcPr.getTcMar
      copyMargin(sourceMar, newMar)
    }

    // 9. 文本方向 (textDirection)
    Option(sourceTcPr.getTextDirection).foreach { sourceTextDir =>
      val newTextDir = if (newTcPr.getTextDirection == null) newTcPr.addNewTextDirection() else newTcPr.getTextDirection
      newTextDir.setVal(sourceTextDir.getVal)
    }

    // 10. 是否允许换行 (noWrap)
    Option(sourceTcPr.getNoWrap).foreach { sourceNoWrap =>
      if (newTcPr.getNoWrap == null) newTcPr.addNewNoWrap()
      // noWrap 是无值属性，存在即表示 true
    }
  }

  /**
   * 复制单元格边框
   */
  def copyCellBorders(src: CTTcBorders, dst: CTTcBorders): Unit = {
    // 上边框
    Option(src.getTop).foreach { sourceTop =>
      val newTop = if (dst.getTop == null) dst.addNewTop() else dst.getTop
      copyBorder(sourceTop, newTop)
    }

    // 下边框
    Option(src.getBottom).foreach { sourceBottom =>
      val newBottom = if (dst.getBottom == null) dst.addNewBottom() else dst.getBottom
      copyBorder(sourceBottom, newBottom)
    }

    // 左边框
    Option(src.getLeft).foreach { sourceLeft =>
      val newLeft = if (dst.getLeft == null) dst.addNewLeft() else dst.getLeft
      copyBorder(sourceLeft, newLeft)
    }

    // 右边框
    Option(src.getRight).foreach { sourceRight =>
      val newRight = if (dst.getRight == null) dst.addNewRight() else dst.getRight
      copyBorder(sourceRight, newRight)
    }

    // 左边框（从左上到右下对角线） - 仅限 Word 2010+
    Option(src.getTl2Br).foreach { sourceTl2Br =>
      val newTl2Br = if (dst.getTl2Br == null) dst.addNewTl2Br() else dst.getTl2Br
      copyBorder(sourceTl2Br, newTl2Br)
    }

    // 右边框（从右上到左下对角线）
    Option(src.getTr2Bl).foreach { sourceTr2Bl =>
      val newTr2Bl = if (dst.getTr2Bl == null) dst.addNewTr2Bl() else dst.getTr2Bl
      copyBorder(sourceTr2Bl, newTr2Bl)
    }
  }

  /**
   * 复制单个边框属性
   */
  def copyBorder(src: CTBorder, dst: CTBorder): Unit = {
    if (src.getVal != null) dst.setVal(src.getVal)
    if (src.getColor != null) dst.setColor(src.getColor)
    if (src.getSz != null) dst.setSz(src.getSz)
    if (src.getSpace != null) dst.setSpace(src.getSpace)
    if (src.getShadow != null) dst.setShadow(src.getShadow)
    if (src.getFrame != null) dst.setFrame(src.getFrame)
  }

  /**
   * 复制单元格内边距
   */
  def copyMargin(src: CTTcMar, dst: CTTcMar): Unit = {
    // 上边距
    if (src.getTop != null) {
      val newTop = if (dst.getTop == null) dst.addNewTop() else dst.getTop
      if (src.getTop.getType != null) newTop.setType(src.getTop.getType)
      if (src.getTop.getW != null) newTop.setW(src.getTop.getW)
    }

    // 下边距
    if (src.getBottom != null) {
      val newBottom = if (dst.getBottom == null) dst.addNewBottom() else dst.getBottom
      if (src.getBottom.getType != null) newBottom.setType(src.getBottom.getType)
      if (src.getBottom.getW != null) newBottom.setW(src.getBottom.getW)
    }

    // 左边距
    if (src.getLeft != null) {
      val newLeft = if (dst.getLeft == null) dst.addNewLeft() else dst.getLeft
      if (src.getLeft.getType != null) newLeft.setType(src.getLeft.getType)
      if (src.getLeft.getW != null) newLeft.setW(src.getLeft.getW)
    }

    // 右边距
    if (src.getRight != null) {
      val newRight = if (dst.getRight == null) dst.addNewRight() else dst.getRight
      if (src.getRight.getType != null) newRight.setType(src.getRight.getType)
      if (src.getRight.getW != null) newRight.setW(src.getRight.getW)
    }
  }
}
