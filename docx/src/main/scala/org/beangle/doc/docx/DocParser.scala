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

import org.apache.poi.openxml4j.util.ZipSecureFile
import org.apache.poi.xwpf.usermodel.*
import org.beangle.commons.collection.Collections
import org.beangle.commons.io.Files
import org.beangle.commons.lang.{Numbers, Strings}
import org.beangle.doc.html
import org.beangle.doc.html.*
import org.openxmlformats.schemas.officeDocument.x2006.sharedTypes.{STTwipsMeasure, STVerticalAlignRun}
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*

import java.io.File
import scala.jdk.javaapi.CollectionConverters.asScala
import scala.util.Random

object DocParser {

  def main(args: Array[String]): Unit = {
    val file = new File(args(0))
    ZipSecureFile.setMinInflateRatio(0.0001d)
    val doc = new XWPFDocument(file.toURI.toURL.openStream())
    val document = new html.Document
    val parser = new DocParser(document)
    parser.parse(doc)
    document.images foreach { (name, data) =>
      java.nio.file.Files.write(new File(file.getParentFile, name).toPath, data)
    }
    val fileName = Strings.substringBeforeLast(file.getName, ".")
    val dochtml = new File(file.getParentFile.getAbsolutePath + Files./ + s"${fileName}.html")
    Files.writeString(dochtml, document.outerHtml)
    println(s"result:${dochtml.getAbsolutePath}")
  }

}

/** WORD文件解析成HTML节点
 *
 * @see http://officeopenxml.com/WPcontentOverview.php
 * @param document
 */
class DocParser(document: html.Document) {

  private var leftMargin = 0d //mm

  private val dxa = "dxa"
  //缺省字体大小 10.5pt，五号字体
  private val defaultFontSize = 10.5f

  private var numberingReader: NumberingReader = _

  def parse(doc: XWPFDocument): Unit = {
    val body = document.body
    document.body.prepend(Dom.wrap("style", ":root{background-color: #f5f5f5;} .m0{margin:0px;}"))
    addDefaultBodyStyle(body)
    readPageSetting(doc)
    readStyles(doc)
    parse(doc.getBodyElements, body)
    doc.close()
  }

  private def addDefaultBodyStyle(body: Dom.Body): Unit = {
    body.addStyles("margin:0mm auto;")
    body.addStyles("background-color:white;box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);")
    body.addStyles(s"font-family:SimSun;font-size:${defaultFontSize}pt;")
    body.addStyle("line-height", "1.5")
  }

  def readStyles(doc: XWPFDocument): Unit = {
    val styles = doc.getStyles

    val contents = new StringBuilder()
    asScala(styles.getStyles) foreach { s =>
      val sid = s.getStyleId
      val name = s.getName
      val prefix = s.getType match {
        case STStyleType.PARAGRAPH => "p"
        case STStyleType.CHARACTER => "c"
        case STStyleType.TABLE => "t"
        case STStyleType.NUMBERING => "n"
      }
      val rpr = s.getCTStyle.getRPr
      if (null != rpr) {
        val bs = Collections.newMap[String, String]
        if (!rpr.getRFontsList.isEmpty) {
          val fonts = readFonts(rpr.getRFontsList.getFirst)
          if (Strings.isNotBlank(fonts)) {
            bs.put("font-family", s"${fonts}")
          }
        }
        if (!rpr.getSzList.isEmpty) {
          val cs = rpr.getSzList.getFirst
          bs.put("font-size", s"${(cs.getVal.asInstanceOf[Number].intValue() / 2.0)}pt")
        }
        if (bs.nonEmpty) {
          contents.append(s".${prefix}${sid}{${new Style(bs.toMap).toString}}")
        }
      }
    }
    document.body.prepend(Dom.wrap("style", contents.toString))
  }

  def parse(elements: java.util.List[IBodyElement], parent: DomNode): Unit = {
    elements.forEach {
      case xp: XWPFParagraph => parent.append(parse(xp))
      case xt: XWPFTable => parent.append(parse(xt))
      case x: Any => println(s"ignore ${x}")
    }
  }

  def parse(xp: XWPFParagraph): Dom.P = {
    val p = new Dom.P
    p.addClass("m0")
    xp.getNumLevelText

    readParagraphProperties(xp, p)
    val runs = xp.getRuns
    //每个Run转换成一个Span
    for (run <- asScala(runs)) {
      var t = run.getText(0)
      if (null == t) t = ""
      if (Strings.isNotEmpty(t)) {
        t = Strings.replace(t, " ", "&ensp;") //半个字符的宽度
        var elem: Element = new Dom.Text(t)
        run.getVerticalAlignment match {
          case STVerticalAlignRun.SUPERSCRIPT => elem = Dom.wrap("sup", elem)
          case STVerticalAlignRun.SUBSCRIPT => elem = Dom.wrap("sub", elem)
          case _ =>
        }
        if (run.isStrikeThrough) elem = Dom.wrap("del", elem)
        if (run.isBold) elem = Dom.wrap("b", elem)
        if (run.isItalic) elem = Dom.wrap("em", elem)

        if (UnderlinePatterns.NONE != run.getUnderline) elem = Dom.wrap("u", elem)

        val span = new Dom.Span(elem)
        p.append(span)
        readRunProperties(run, span)
      }

      asScala(run.getEmbeddedPictures) foreach { ep =>
        val img = new Dom.Img
        val epd = ep.getPictureData
        img.addStyle("width", ep.getWidth.toString + "pt")
        img.addAttribute("src", epd.getFileName)
        if (Strings.isNotBlank(ep.getDescription)) {
          img.addAttribute("alt", ep.getDescription)
        }
        document.newImage(ep.getPictureData.getFileName, epd.getData)
        p.append(img)
      }
    }
    //为了保留一个段落的高度
    if (p.children.isEmpty) {
      p.append(new Dom.Br)
    }
    p.mergeSpan()
    p
  }

  def parse(xt: XWPFTable): Table = {
    val t = new Table
    val tbody = t.newBody()

    readTablePosition(xt, t)
    readTableBorder(xt, t)
    //FIXME
    //tblPr.getTblCellMar
    //每列的宽度
    val grid = xt.getCTTbl.getTblGrid
    if (null != grid) {
      val cg = t.newColGroup()
      asScala(grid.getGridColList).foreach { xcol =>
        val col = cg.newCol()
        if (xcol.isSetW) col.addStyle("width", s"${dxaToMM(xcol.xgetW)}mm")
      }
    }
    readTableProperties(xt.getCTTbl.getTblPr, t)
    //处理每一行
    asScala(xt.getRows) foreach { xr =>
      val tr = tbody.newRow()
      asScala(xr.getTableCells) foreach { xc =>
        val cell = tr.newCell()
        val tcpr = xc.getCTTc.getTcPr
        if (tcpr != null) {
          //背景颜色
          val ctshd = tcpr.getShd
          if (ctshd != null && ctshd.isSetFill) {
            val color = ctshd.xgetFill().getStringValue
            if (null != color && color != "auto") {
              cell.addStyle("background-color", "#" + color)
            }
          }
          //竖直对齐方式
          if (null != tcpr.getVAlign) {
            tcpr.getVAlign.getVal match {
              case STVerticalJc.CENTER => cell.addAttribute("vertical-align", "middle")
              case STVerticalJc.BOTTOM => cell.addAttribute("vertical-align", "bottom")
              case _ =>
            }
          }
          //跨列
          if (null != tcpr.getGridSpan) {
            val colspan = tcpr.getGridSpan.getVal.intValue()
            if (colspan > 1) {
              cell.addAttribute("colspan", colspan.toString)
            }
          }
        }
        parse(xc.getBodyElements, cell)
      }
    }
    t
  }

  /** 读取段落上的属性
   *
   * @param ppr
   * @return
   */
  private def readParagraphProperties(xp: XWPFParagraph, p: DomNode): Unit = {
    val ppr = xp.getCTPPr
    if (null != ppr) {
      val pstyle = ppr.getPStyle
      if (null != pstyle) {
        p.addClass(s"p${pstyle.getVal}")
      }
      //读取缩进
      val ind = ppr.getInd
      if (null != ind) {
        //整体缩进
        if (ind.isSetLeft) {
          val l = Length(ind.xgetLeft().getStringValue, dxa)
          p.addStyle("margin-left", l.toMM)
        }
        //首行缩进
        if (ind.isSetFirstLine) {
          val l = Length(ind.xgetFirstLine().getStringValue, dxa)
          p.addStyle("text-indent", l.toMM)
        }
        //悬挂缩进
        if (ind.isSetHanging) {
          val l = Length(ind.xgetHanging().getStringValue, dxa)
          p.addStyle("text-indent", s"-${l}")
          p.addStyle("padding-left", l.toMM)
        }
      }
      //对齐方式 Alignment/Justification    //对齐方式
      if (ppr.isSetJc) {
        val align = ppr.getJc.xgetVal()
        if (null != align) {
          val alignStyle = align.getStringValue match {
            case "right" => "right"
            case "center" => "center"
            case "both" => "justify"
            case _ => "left"
          }
          if alignStyle != "left" then
            p.addStyle("text-align", alignStyle)
        }
      }
      //字体和字体大小
      var fontsInPt = defaultFontSize
      if (ppr.isSetRPr) {
        val rpr = ppr.getRPr
        val sz = rpr.getSzList
        if (!sz.isEmpty) {
          fontsInPt = sz.getFirst.getVal.asInstanceOf[Number].intValue / 2.0f
          p.addStyle("font-size", s"${fontsInPt}pt")
        }
        val fs = rpr.getRFontsList
        if (!fs.isEmpty) {
          val fonts = readFonts(fs.getFirst)
          if Strings.isNotBlank(fonts) then p.addStyle("font-family", fonts)
        }
      }

      //行高
      //@see http://officeopenxml.com/WPspacing.php
      if (ppr.isSetSpacing) {
        val spacing = ppr.getSpacing
        if (spacing.isSetLineRule && spacing.isSetLine) {
          val rule = spacing.xgetLineRule().getStringValue
          val v = spacing.xgetLine().getStringValue.toInt
          rule match {
            case "auto" => p.addStyle("line-height", v / 240.0)
            case "exact" => p.addStyle("line-height", s"${(v / 20.0)}pt")
            case "atLeast" => //有的制定了最小行高，css不支持
          }
        }

        if (spacing.isSetBefore) {
          var autoSpacing = false
          if (spacing.isSetBeforeAutospacing) {
            autoSpacing = spacing.getBeforeAutospacing.asInstanceOf[java.lang.Boolean].booleanValue
          }
          if (!autoSpacing) {
            p.addStyle("margin-top", s"${Length(spacing.xgetBefore().getStringValue, dxa).toMM}")
          }
        }
      }

      if (ppr.isSetNumPr) {
        val numbering = readNumbering(xp)
        if (Strings.isNotBlank(numbering)) {
          val numSpan = new Dom.Span(numbering)
          numSpan.addClass("idx")
          p.prepend(numSpan)
        }
      }
    }
  }

  private def readNumbering(xp: XWPFParagraph): String = {
    if (this.numberingReader == null) {
      numberingReader = new NumberingReader(xp.getDocument.getNumbering)
    }
    numberingReader.extract(xp)
  }

  /** 读取连续文本上的属性
   *
   * @param rpr
   * @return
   */
  private def readRunProperties(run: XWPFRun, elem: DomNode): Unit = {
    val rpr = run.getCTR.getRPr
    val p = elem.parent.get
    if (null != rpr) {
      //字号
      val sz = rpr.getSzList
      if (!sz.isEmpty) {
        val parentFontSize = p.computedStyle.properties.getOrElse("font-size", "--")
        val fontSize = s"${sz.getFirst.getVal.asInstanceOf[Number].intValue / 2.0f}pt"
        if (parentFontSize != fontSize) elem.addStyle("font-size", fontSize)
      }
      //字体
      val fs = rpr.getRFontsList
      if (!fs.isEmpty) {
        val fonts = readFonts(fs.getFirst)
        if (Strings.isNotBlank(fonts)) {
          val parentFontFamily = p.computedStyle.properties.getOrElse("font-family", "--")
          if (parentFontFamily != fonts) elem.addStyle("font-family", fonts)
        }
      }
      //颜色
      val colors = rpr.getColorList
      if (!colors.isEmpty) {
        val color = colors.getFirst.xgetVal().getStringValue
        if (Strings.isNotBlank(color)) {
          val parentColor = p.computedStyle.properties.getOrElse("color", "--")
          if (parentColor != color) elem.addStyle("color", s"#$color")
        }
      }
    }
  }

  private def readTableProperties(tblPr: CTTblPr, t: Table): Unit = {
    //表格位置，缩进
    if (tblPr.isSetTblInd) {
      addStyle(t, "margin-left", tblPr.getTblInd)
    }
    //表格宽度
    if (tblPr.isSetTblW) {
      addStyle(t, "width", tblPr.getTblW)
    }
    //表格水平布局
    val tblJc = tblPr.getJc
    if (null != tblJc) {
      tblJc.getVal match {
        case STJcTable.CENTER => t.addStyle("margin", "auto")
        case STJcTable.RIGHT => t.addStyle("margin", "auto 0 auto auto")
        case _ =>
      }
    } else {
      t.addStyle("margin", "auto")
    }
  }

  private def readFonts(fs: CTFonts): String = {
    val fonts = Collections.newBuffer[String]
    val hint = fs.getHint
    val ascii = changeFontName(fs.getAscii)
    val eastAsia = changeFontName(fs.getEastAsia)

    //目前忽略 1) 复杂文种字体 fs.getHAnsi High ANSI 2)复杂脚本 fs.getCs Complex Script
    if (null != hint) {
      return eastAsia
    } else if (null != ascii || null != eastAsia) {
      //西文字体 ASCII
      if (null != ascii) {
        fonts.addOne(ascii)
      }
      //中文字体 East Asian
      if (null != eastAsia && !fonts.contains(eastAsia)) {
        fonts.addOne(eastAsia)
      }
    }
    fonts.mkString(",")
  }

  private def changeFontName(fontName: String): String = {
    if (null == fontName) fontName
    else if (fontName.contains("_GB2312")) fontName.replace("_GB2312", "")
    else fontName
  }

  /** 读取表格定位
   * 是浮动表格还是绝对定位
   *
   * @see http://officeopenxml.com/WPfloatingTables.php
   * @param xt
   * @param t
   */
  private def readTablePosition(xt: XWPFTable, t: Table): Unit = {
    if (xt.getCTTbl.getTblPr.isSetTblpPr) {
      val ppr = xt.getCTTbl.getTblPr.getTblpPr
      val x = ppr.getTblpX
      if (null != x) {
        val left = Length.dxaToMM(x.asInstanceOf[Number].intValue()).num
        t.addStyles(s"position:relative;left:${left - this.leftMargin}mm")
      }
    }
  }

  private def readTableBorder(xt: XWPFTable, t: Table): Unit = {
    val top = parseBorderData("top", xt.getTopBorderSize, xt.getTopBorderColor, xt.getTopBorderType)
    val right = parseBorderData("right", xt.getRightBorderSize, xt.getRightBorderColor, xt.getRightBorderType)
    val bottom = parseBorderData("bottom", xt.getBottomBorderSize, xt.getBottomBorderColor, xt.getBottomBorderType)
    val left = parseBorderData("left", xt.getLeftBorderSize, xt.getLeftBorderColor, xt.getLeftBorderType)
    t.addStyles(top.toCss)
    t.addStyles(right.toCss)
    t.addStyles(bottom.toCss)
    t.addStyles(left.toCss)
    t.addStyle("border-collapse", "collapse")
    var tableId = t.attributes.getOrElse("id", "")
    if (Strings.isEmpty(tableId)) {
      tableId = "tb" + Random.nextInt(99999)
    }
    t.addAttribute("id", tableId)

    var cellStyle = s"#$tableId td {"
    val ih = parseBorderData("top", xt.getInsideHBorderSize, xt.getInsideHBorderColor, xt.getInsideHBorderType)
    cellStyle += ih.toCss + ih.at("bottom").toCss
    val iv = parseBorderData("left", xt.getInsideVBorderSize, xt.getInsideVBorderColor, xt.getInsideVBorderType)
    cellStyle += ih.toCss + ih.at("right").toCss
    cellStyle += "}"

    t.prepend(Dom.wrap("style", cellStyle))
  }

  private def parseBorderData(side: String, borderSize: Int, color: String, style: XWPFTable.XWPFBorderType): BorderData = {
    val c = if (color == "auto") "black" else "#" + color
    val s = style match
      case XWPFTable.XWPFBorderType.NONE => "none"
      case XWPFTable.XWPFBorderType.SINGLE => "solid"
      case XWPFTable.XWPFBorderType.DOUBLE => "double"
      case _ => "solid"
    val b = if borderSize < 0 then 0 else borderSize / 8.0 // 1/8 points
    new BorderData(side, s"${b}px", c, s)
  }

  /** 读取word中的页面设置
   *
   * @param doc
   */
  private def readPageSetting(doc: XWPFDocument): Unit = {
    val secPr = doc.getDocument.getBody.getSectPr
    val mar = secPr.getPgMar
    val body = document.body
    if (null != mar) {
      val top = dxaToMM(mar.xgetTop)
      val right = dxaToMM(mar.xgetRight)
      val bottom = dxaToMM(mar.xgetBottom)
      val left = dxaToMM(mar.xgetLeft)
      leftMargin = left
      body.addStyle("padding", s"${top}mm ${right}mm ${bottom}mm ${left}mm")
      body.addStyle("width", s"${Numbers.round(210 - left - right, 2)}mm")
    }
  }

  private def dxaToMM(m: STSignedTwipsMeasure): Double = {
    Length(m.getStringValue, dxa).mm
  }

  private def dxaToMM(m: STTwipsMeasure): Double = {
    Length(m.getStringValue, dxa).mm
  }

  private def addStyle(n: DomNode, name: String, w: CTTblWidth): Unit = {
    w.getType match {
      case STTblWidth.DXA =>
        val dxa = w.getW.asInstanceOf[Number].intValue
        if (dxa > 0) n.addStyle(name, s"${Length.dxa(dxa).toMM}")
      case STTblWidth.PCT =>
        val pct = w.getW.asInstanceOf[Number].doubleValue() / 50
        n.addStyle(name, s"${pct}%")
      case x: Any =>
    }
  }
}
