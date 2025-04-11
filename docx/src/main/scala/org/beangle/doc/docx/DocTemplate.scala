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
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.{XWPFDocument, XWPFParagraph, XWPFRun}
import org.beangle.commons.activation.{MediaType, MediaTypes}
import org.beangle.commons.codec.binary.Base64
import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.{Chars, Strings}
import org.beangle.commons.logging.Logging
import org.beangle.template.api.{TemplateEngine, TemplateInterpreter}
import org.beangle.template.freemarker.DefaultTemplateEngine

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.net.URL

object DocTemplate {

  def process(url: URL, data: collection.Map[String, Any]): Array[Byte] = {
    val templateIs = url.openStream()
    val doc = new XWPFDocument(templateIs)
    val template = new DocTemplate(doc, DefaultTemplateEngine())
    val bytes = template.process(data)
    templateIs.close()
    bytes
  }

  def main(args: Array[String]): Unit = {
    val template = new DocTemplate(null, DefaultTemplateEngine())
    val rs = template.splitImg("dadffa 申请人签名：[#img src=step0_esign height=\"10mm\" width=\"30mm\" /]${step0_auditAt}[#img src=step0_esign height=\"10mm\" width=\"30mm\" /]")
    println(template.splitImg("dadffa 申请人签名：${dd}"))
    println(rs)
  }
}

class DocTemplate(doc: XWPFDocument, engine: TemplateEngine) extends Logging {

  private val interpreter = new TemplateInterpreter(engine)

  private var imageIndex = 0

  def process(data: collection.Map[String, Any]): Array[Byte] = {
    import scala.jdk.javaapi.CollectionConverters.*

    for (p <- asScala(doc.getParagraphs)) {
      mergeRun(p)
      val runs = p.getRuns
      if (runs != null) {
        for (r <- asScala(runs)) fillin(r, data)
      }
    }

    for (tbl <- asScala(doc.getTables)) {
      for (row <- asScala(tbl.getRows)) {
        for (cell <- asScala(row.getTableCells)) {
          for (p <- asScala(cell.getParagraphs)) {
            mergeRun(p)
            for (r <- asScala(p.getRuns)) fillin(r, data)
          }
        }
      }
    }
    val bos = new ByteArrayOutputStream()
    doc.write(bos)
    bos.toByteArray
  }

  private def mergeRun(p: XWPFParagraph): Unit = {
    val runs = p.getRuns
    if (runs != null) {
      var i = 0
      val runIter = runs.iterator()

      val headText = new StringBuilder()
      var headRun: Option[XWPFRun] = None
      val removed = Collections.newBuffer[Int]
      while (runIter.hasNext) {
        val run = runIter.next()
        val text = run.getText(0)

        if (headRun.nonEmpty) {
          if (Strings.isNotEmpty(text)) {
            headText.addAll(text)
            if (isExpEnd(text)) {
              headRun.get.setText(headText.mkString, 0)
              headText.clear()
              headRun = None
            }
          }
          removed.addOne(i)
        } else {
          if (null != text) {
            if (isExpStart(text)) {
              headRun = Some(run)
              headText.addAll(text)
            }
          }
        }
        i += 1
      }
      //从较大的序号开始删除，保证原始序列的编号稳定性
      removed.reverse foreach { i =>
        p.removeRun(i)
      }
    }
  }

  private def fillin(run: XWPFRun, data: collection.Map[String, Any]): Unit = {
    val rs = splitImg(DocHelper.readText(run))
    //生成所有的图片
    val imgs = Collections.newMap[String, Picture]
    rs._2 foreach { case (imgName, tag) =>
      val propertyStr = Strings.substringBetween(tag, "[#img ", "/]")
      val p = Strings.split(propertyStr, "=").flatMap(Strings.split)
      val properties = Collections.newMap[String, String]
      var i = 1
      while (i < p.length) {
        if p(i).startsWith("\"") then
          val v = Strings.substringBetween(p(i), "\"", "\"")
          properties.put(p(i - 1), v)
        else if (data.contains(p(i))) {
          properties.put(p(i - 1), data(p(i)).toString)
        }
        i += 2
      }
      if (properties.contains("src")) {
        var src = properties("src")
        if (src.contains("base64,")) {
          src = Strings.substringAfter(src, "base64,")
        }
        val width = toEmu(properties("width"))
        val height = toEmu(properties("height"))
        val mediaType = MediaTypes.ImagePng
        imgs.put(imgName, Picture(new ByteArrayInputStream(Base64.decode(src)), mediaType, generateImgName(mediaType), width, height))
      }
    }

    var text = rs._1
    var changed = rs._2.nonEmpty

    //处理缩放
    val si = scale(text, run)
    text = si._2
    //解析变量
    if (text.contains("${")) {
      text = interpret(text, data)
    }
    //实施缩放
    if (si._1 > 0) {
      val max = si._1
      val resultLen = Chars.charLength(text)
      if (resultLen > max) {
        val scale = java.lang.Double.valueOf(max * 100.0 / resultLen).toInt
        run.setTextScale(scale)
      }
    }
    //开始填入
    if text != rs._1 then changed = true
    if (changed) {
      if (rs._2.isEmpty) {
        DocHelper.set(run, text)
      } else {
        val results = Collections.newBuffer[Any]
        var pIdx = 0
        rs._2.keys.toSeq.sorted foreach { imgName =>
          val imgIdx = text.indexOf(imgName, pIdx)
          results.addOne(text.substring(pIdx, imgIdx))
          pIdx += (imgIdx + imgName.length)
          imgs.get(imgName) match {
            case None => results.addOne("")
            case Some(p) => results.addOne(p)
          }
        }
        if (pIdx < text.length) {
          results.addOne(text.substring(pIdx))
        }
        DocHelper.set(run, results)
      }
    }
  }

  private def interpret(text: String, data: Any): String = {
    var template = text.trim()
    val start = "${"
    val end = "}"
    var processIdx = 0
    while (template.indexOf(start, processIdx) >= processIdx && template.indexOf(end, processIdx) >= processIdx) {
      val startIdx = template.indexOf(start, processIdx)
      val endIdx = template.indexOf(end, processIdx)
      if (startIdx >= 0 && endIdx > startIdx) {
        var exp = template.substring(startIdx + start.length, endIdx).trim()
        if (!exp.startsWith("(") && !exp.endsWith(")!")) {
          exp = s"(${exp})!"
          val sb = new StringBuilder(template)
          sb.replace(startIdx + start.length, endIdx, exp)
          template = sb.toString()
        }
      }
      if (endIdx > processIdx) {
        processIdx = endIdx + 1
      } else {
        processIdx = template.length
      }
    }
    try
      interpreter.process(template, data)
    catch
      case ex: Exception =>
        logger.error(s"process ${text} error", ex)
        text
  }

  private def generateImgName(mediaType: MediaType): String = {
    imageIndex += 1
    s"img${this.imageIndex}.${mediaType.subType}"
  }

  private def toEmu(num: String): Int = {
    if (num.endsWith("m")) {
      if (num.endsWith("mm")) {
        Strings.replace(num, "mm", "").toInt * Units.EMU_PER_CENTIMETER / 10
      } else if (num.endsWith("cm")) {
        Strings.replace(num, "cm", "").toInt * Units.EMU_PER_CENTIMETER
      } else {
        throw new RuntimeException(s"Cannot parse ${num} to emu")
      }
    } else {
      num.toInt * Units.EMU_PER_CENTIMETER
    }
  }

  private def isExpStart(text: String): Boolean = {
    text.contains("${") || text.contains("[#")
  }

  private def isExpEnd(text: String): Boolean = {
    val expStart = text.lastIndexOf("${")
    val expEnd = text.lastIndexOf("}")
    val directiveStart = text.lastIndexOf("[#")
    val directiveEnd = text.lastIndexOf("]")

    expEnd > -1 && expEnd > expStart || directiveEnd > -1 && directiveEnd > directiveStart
  }

  private def scale(text: String, r: XWPFRun): (Int, String) = {
    if (text.contains("[#maxlen")) {
      val max = Integer.parseInt(Strings.substringBetween(text, "[#maxlen", "]").trim())
      val processed = removeDirective(text, "[#maxlen", "]")
      (max, processed)
    } else {
      (-1, text)
    }
  }

  private def removeDirective(text: String, start: String, end: String): String = {
    if (text.contains(start)) {
      val startIdx = text.indexOf(start)
      val endIdx = text.indexOf(end, startIdx)
      if (startIdx > 0 && endIdx > startIdx) {
        new StringBuilder(text).delete(startIdx, endIdx + end.length).toString()
      } else {
        text
      }
    } else {
      text
    }
  }

  /** 将[#img指令拆成独立的片段
   *
   * @param text
   * @return
   */
  protected[docx] def splitImg(text: String): (String, collection.Map[String, String]) = {
    if (null == text || text == "") return (text, Map.empty)

    val results = Collections.newBuffer[String]
    val sb = new StringBuilder()
    val imgs = Collections.newMap[String, String]
    val start = "[#img"
    val end = "]"
    var processIdx = 0
    var imgIdx = 0
    while (text.indexOf(start, processIdx) >= processIdx && text.indexOf(end, processIdx) >= processIdx) {
      val startIdx = text.indexOf(start, processIdx)
      val endIdx = text.indexOf(end, processIdx)
      if (startIdx >= 0 && endIdx > startIdx) {
        val img = text.substring(startIdx, endIdx + 1)
        sb.addAll(text.substring(processIdx, startIdx))
        imgIdx += 1
        val imgTag = s"#img${imgIdx}#"
        imgs.put(imgTag, img)
        sb.addAll(imgTag)
      }
      if (endIdx > processIdx) {
        processIdx = endIdx + 1
      } else {
        processIdx = text.length
      }
    }
    if (processIdx < text.length) {
      sb.addAll(text.substring(processIdx))
    }
    (sb.toString(), imgs)
  }
}
