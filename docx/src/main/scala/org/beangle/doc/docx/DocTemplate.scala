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
import org.apache.poi.xwpf.usermodel.*
import org.beangle.commons.activation.{MediaType, MediaTypes}
import org.beangle.commons.codec.binary.Base64
import org.beangle.commons.collection.Collections
import org.beangle.commons.conversion.string.BooleanConverter
import org.beangle.commons.io.IOs
import org.beangle.commons.lang.{Chars, Strings}
import org.beangle.template.api.TemplateInterpreter
import org.beangle.template.freemarker.DefaultTemplateInterpreter

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, OutputStream}
import java.net.URL
import scala.jdk.javaapi.CollectionConverters.*

object DocTemplate {

  def process(url: URL, data: collection.Map[String, Any]): Array[Byte] = {
    val templateIs = url.openStream()
    try {
      val doc = new XWPFDocument(templateIs)
      new DocTemplate(doc).process(data)
    } finally {
      templateIs.close()
    }
  }

  /** 对每组变量完整渲染一次模板，按顺序拼接为一个文档，每份之间插入分页符。
   *
   * 先 `DocMerger.copyN` 复制 N 份未渲染模板（含分页），再逐段 `DocTemplate.fillBodyRange` 渲染。
   */
  def processAll(url: URL, items: Iterable[collection.Map[String, Any]]): Array[Byte] = {
    val itemList = items.toSeq
    if itemList.isEmpty then {
      val is = url.openStream()
      return try IOs.readBytes(is) finally is.close()
    }

    var outputDoc: XWPFDocument = null
    try {
      val (doc, slotRanges) = DocMerger.copyN(url, itemList.size)
      outputDoc = doc
      val template = new DocTemplate(outputDoc)
      itemList.zip(slotRanges).foreach { case (data, (from, until)) =>
        template.fillBodyRange(from, until, data)
      }
      val bos = new ByteArrayOutputStream()
      outputDoc.write(bos)
      bos.toByteArray
    } finally {
      if outputDoc != null then outputDoc.close()
    }
  }
}

class DocTemplate(doc: XWPFDocument, interpreter: TemplateInterpreter = DefaultTemplateInterpreter) {

  private var imageIndex = 0

  def process(data: collection.Map[String, Any]): Array[Byte] = {
    fill(data)
    toBytes
  }

  def fill(data: collection.Map[String, Any]): Unit = {
    fillBodyRange(0, doc.getBodyElements.size(), data)
  }

  /** 仅渲染正文中 `[bodyFrom, bodyUntil)` 范围内的段落与表格（供 `DocTemplate.processAll` 按份填充）。 */
  def fillBodyRange(bodyFrom: Int, bodyUntil: Int, data: collection.Map[String, Any]): Unit = {
    if bodyFrom >= bodyUntil then return
    asScala(doc.getBodyElements).slice(bodyFrom, bodyUntil) foreach {
      case p: XWPFParagraph => fillParagraph(p, data)
      case tbl: XWPFTable => fillTable(tbl, data)
      case _ =>
    }
  }

  /** 供测试：合并段落后返回各 run 文本。 */
  private[docx] def mergeParagraphTexts(p: XWPFParagraph): Seq[String] = {
    mergeScriptRuns(p)
    asScala(p.getRuns).toSeq.map(r => Option(DocHelper.readText(r)).getOrElse(""))
  }

  private def fillParagraph(p: XWPFParagraph, data: collection.Map[String, Any]): Unit = {
    mergeScriptRuns(p)
    val runs = p.getRuns
    if runs != null then
      asScala(runs).toSeq.foreach { r => fillin(r, data) }
  }

  private def fillTable(tbl: XWPFTable, data: collection.Map[String, Any]): Unit = {
    for (row <- asScala(tbl.getRows)) {
      for (cell <- asScala(row.getTableCells)) {
        for (p <- asScala(cell.getParagraphs)) fillParagraph(p, data)
      }
    }
  }

  def write(out: OutputStream): Unit = {
    doc.write(out)
  }

  def toBytes: Array[Byte] = {
    val bos = new ByteArrayOutputStream()
    doc.write(bos)
    bos.toByteArray
  }

  /** 仅合并含 `${…}`、`[#…]` 模板表达式的跨 run 片段（Word 常把 `[` 等拆成独立 run）。
   *
   * 不含表达式的 run 不参与合并、原样保留，以便维持段落中其余片段的格式（字体、颜色等）。
   * 相邻或重叠的跨 run 区间会先合并（如 1–2 与 2–5 → 1–5），再写入区间首 run，最后从后向前删除多余 run。
   *
   * @param p 待处理的段落
   */
  private def mergeScriptRuns(p: XWPFParagraph): Unit = {
    val runs = asScala(p.getRuns).toSeq
    if runs.isEmpty then return

    val texts = runs.map(r => Option(DocHelper.readText(r)).getOrElse(""))
    val full = texts.mkString
    if full.isEmpty then return

    val runStarts = texts.scanLeft(0)(_ + _.length).init

    def runIndexAt(pos: Int): Int =
      if pos < 0 then -1 else runStarts.lastIndexWhere(_ <= pos)

    val intervals = findTemplateRegions(full).flatMap { case (start, end) =>
      val rStart = runIndexAt(start)
      val rEnd = runIndexAt(math.max(start, end - 1))
      if rStart >= 0 && rEnd > rStart then Some((rStart, rEnd)) else None
    }
    if intervals.isEmpty then return

    val merged = mergeRunIntervals(intervals)
    merged.foreach { case (lo, hi) =>
      runs(lo).setText(texts.slice(lo, hi + 1).mkString, 0)
    }
    val toRemove = merged.flatMap { case (lo, hi) => lo + 1 to hi }.distinct.sorted.reverse
    toRemove.foreach(p.removeRun)
  }

  /** 合并重叠或相邻的 run 区间（如 (1,2) 与 (2,5) → (1,5)）。 */
  private def mergeRunIntervals(intervals: Seq[(Int, Int)]): Seq[(Int, Int)] = {
    if intervals.isEmpty then return Seq.empty
    val sorted = intervals.sortBy(_._1)
    val merged = Collections.newBuffer[(Int, Int)]
    var (curLo, curHi) = sorted.head
    sorted.tail.foreach { case (lo, hi) =>
      if lo <= curHi then curHi = math.max(curHi, hi)
      else
        merged.addOne((curLo, curHi))
        curLo = lo
        curHi = hi
    }
    merged.addOne((curLo, curHi))
    merged.toSeq
  }

  /** 在段落拼接文本中定位需保持完整的模板区间 `[start, end)`。 */
  private def findTemplateRegions(text: String): Seq[(Int, Int)] = {
    val regions = Collections.newBuffer[(Int, Int)]
    var i = 0
    while i < text.length do {
      if i + 1 < text.length && text(i) == '$' && text(i + 1) == '{' then
        val end = text.indexOf('}', i + 2)
        if end >= 0 then {
          regions.addOne((i, end + 1))
          i = end + 1
        } else i += 1
      else if i + 1 < text.length && text(i) == '[' && text(i + 1) == '#' then
        val end = text.indexOf("/]", i + 2)
        if end >= 0 then {
          regions.addOne((i, end + 2))
          i = end + 2
        } else i += 1
      else i += 1
    }
    regions.toSeq
  }

  /** 供测试：定位段落文本中的模板区间。 */
  protected[docx] def templateRegions(text: String): Seq[(Int, Int)] = findTemplateRegions(text)

  private def fillin(run: XWPFRun, data: collection.Map[String, Any]): Unit = {
    val rawText = DocHelper.readText(run)
    val (textWithPh, directives) = extractDirectives(rawText)
    var text = textWithPh

    val maxLen = directives.collectFirst {
      case Directive("maxlen", _, Some(n), _) => n.trim.toIntOption
    }.flatten.getOrElse(-1)
    text = directives.filter(_.kind == "maxlen").foldLeft(text)((t, d) => t.replace(d.placeholder, ""))

    if text.contains("${") then text = interpreter.process(text, data)

    if maxLen > 0 then {
      val resultLen = Chars.charLength(text)
      if resultLen > maxLen then {
        val scale = java.lang.Double.valueOf(maxLen * 100.0 / resultLen).toInt
        run.setTextScale(scale)
      }
    }

    val fillables = Collections.newMap[String, Any]
    directives.foreach {
      case d@Directive("img", ph, _, props) =>
        buildPicture(resolveProperties(props, data)).foreach(p => fillables.put(ph, p))
      case d@Directive("checkbox", ph, name, _) =>
        name.foreach { n =>
          fillables.put(ph, DocHelper.CheckboxGlyph(resolveCheckbox(n, data)))
        }
      case _ =>
    }

    val changed = text != rawText || fillables.nonEmpty
    if !changed then return

    val placeholders = placeholdersInText(text, fillables.keys)
    if placeholders.isEmpty then DocHelper.set(run, text)
    else {
      val results = Collections.newBuffer[Any]
      var pIdx = 0
      placeholders.foreach { tag =>
        val tagIdx = text.indexOf(tag, pIdx)
        if tagIdx >= 0 then {
          if tagIdx > pIdx then results.addOne(text.substring(pIdx, tagIdx))
          fillables.get(tag).foreach(results.addOne)
          pIdx = tagIdx + tag.length
        }
      }
      if pIdx < text.length then results.addOne(text.substring(pIdx))
      applyFill(run, results.toSeq)
    }
  }

  private def resolveProperties(props: Map[String, String], data: collection.Map[String, Any]): Map[String, String] =
    props.map { case (k, v) => (k, resolvePropertyValue(v, data)) }

  private def resolvePropertyValue(raw: String, data: collection.Map[String, Any]): String = {
    val literal = stripQuotes(Strings.trim(raw))
    if data.contains(literal) then data(literal).toString else literal
  }

  private def buildPicture(props: Map[String, String]): Option[Picture] = {
    if !props.contains("src") then None
    else {
      var src = props("src")
      if src.contains("base64,") then src = Strings.substringAfter(src, "base64,")
      val width = toEmu(props.getOrElse("width", "0"))
      val height = toEmu(props.getOrElse("height", "0"))
      val mediaType = MediaTypes.png
      try {
        val bytes = Base64.decode(src)
        Some(Picture(new ByteArrayInputStream(bytes), mediaType, generateImgName(mediaType), width, height))
      } catch {
        case _: Exception => None
      }
    }
  }

  private def parseCheckboxValue(value: Any): Boolean = {
    value match {
      case b: Boolean => b
      case _ => BooleanConverter(value.toString)
    }
  }

  /** checkbox 变量一律经模板求值，并用 `?c` 格式化为 `true`/`false` 字符串。 */
  private def resolveCheckbox(name: String, data: collection.Map[String, Any]): Boolean = {
    val template = checkboxTemplate(name)
    if template.isEmpty then false
    else parseCheckboxValue(interpreter.process(template, data))
  }

  private def checkboxTemplate(name: String): String = {
    if name.isEmpty then ""
    else if name.endsWith("?c") then s"$${$name}"
    else s"$${(($name)!false)?c}"
  }


  /** 按片段写入 run；含复选框或图片时拆成多个 run（字体不同）。 */
  private def applyFill(run: XWPFRun, components: Seq[Any]): Unit = {
    val needsSplit = components.size > 1 || components.exists {
      case _: DocHelper.CheckboxGlyph | _: Picture => true
      case _ => false
    }
    if !needsSplit then
      components.headOption foreach {
        case c: DocHelper.CheckboxGlyph => DocHelper.applyCheckbox(run, c.checked)
        case other => DocHelper.set(run, other.toString)
      }
    else
      applySplitRuns(run.getParagraph, run, components)
  }

  private def applySplitRuns(p: XWPFParagraph, firstRun: XWPFRun, components: Seq[Any]): Unit = {
    var pos = p.getRuns.indexOf(firstRun)
    if pos < 0 then
      components.foreach {
        case s: String => DocHelper.set(firstRun, s)
        case pic: Picture =>
          val pictureType = PictureType.valueOf(pic.mediaType.subType.toUpperCase)
          firstRun.addPicture(pic.is, pictureType, pic.filename, pic.width, pic.height)
        case c: DocHelper.CheckboxGlyph => DocHelper.applyCheckbox(firstRun, c.checked)
        case other => DocHelper.set(firstRun, other.toString)
      }
      return
    components.zipWithIndex.foreach { case (component, i) =>
      val target =
        if i == 0 then firstRun
        else {
          pos += 1
          p.insertNewRun(pos)
        }
      component match {
        case s: String => DocHelper.set(target, s)
        case pic: Picture =>
          val pictureType = PictureType.valueOf(pic.mediaType.subType.toUpperCase)
          target.addPicture(pic.is, pictureType, pic.filename, pic.width, pic.height)
        case c: DocHelper.CheckboxGlyph => DocHelper.applyCheckbox(target, c.checked)
        case other => DocHelper.set(target, other.toString)
      }
    }
  }

  private def generateImgName(mediaType: MediaType): String = {
    imageIndex += 1
    s"img${imageIndex}.${mediaType.subType}"
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

  private def placeholdersInText(text: String, keys: Iterable[String]): Seq[String] =
    keys.toSeq.filter(text.contains).sortBy(text.indexOf(_))

  /** 一次性解析文本中全部 `[#kind … /]` 指令，正文替换为 `#kind1#` 占位符。 */
  protected[docx] def extractDirectives(text: String): (String, Seq[Directive]) = {
    if null == text || text == "" then return (text, Seq.empty)

    val sb = new StringBuilder()
    val list = Collections.newBuffer[Directive]
    val kindIndex = Collections.newMap[String, Int]
    var processIdx = 0
    while processIdx < text.length do {
      val startIdx = text.indexOf("[#", processIdx)
      if startIdx < 0 then {
        sb.addAll(text.substring(processIdx))
        processIdx = text.length
      } else {
        sb.addAll(text.substring(processIdx, startIdx))
        val slashEnd = text.indexOf("/]", startIdx)
        if slashEnd < 0 then {
          sb.addAll(text.substring(startIdx))
          processIdx = text.length
        } else {
          val full = text.substring(startIdx, slashEnd + 2)
          val kind = directiveKind(full)
          if Strings.isEmpty(kind) then {
            sb.addAll("[#")
            processIdx = startIdx + 2
          } else {
            val body = directiveBody(full, kind)
            val (name, props) = parseDirectiveBody(body)
            val n = kindIndex.getOrElse(kind, 0) + 1
            kindIndex.put(kind, n)
            val placeholder = s"#${kind}${n}#"
            list.addOne(Directive(kind, placeholder, name, props.toMap))
            sb.addAll(placeholder)
            processIdx = slashEnd + 2
          }
        }
      }
    }
    (sb.toString(), list.toSeq)
  }

  private def directiveKind(full: String): String = {
    var i = 2
    val kind = new StringBuilder()
    while i < full.length && full(i) != ' ' && full(i) != '/' do {
      kind.append(full(i))
      i += 1
    }
    kind.toString
  }

  private def directiveBody(full: String, kind: String): String = {
    val head = s"[#$kind"
    if !full.startsWith(head) then ""
    else if full.length <= head.length + 2 then ""
    else if full(head.length) == ' ' then Strings.trim(full.substring(head.length + 1, full.length - 2))
    else Strings.trim(full.substring(head.length, full.length - 2))
  }

  private def parseDirectiveBody(body: String): (Option[String], collection.Map[String, String]) = {
    val trimmed = Strings.trim(body)
    if Strings.isEmpty(trimmed) then (None, Map.empty)
    else if !trimmed.contains("=") then (Some(stripQuotes(trimmed)), Map.empty)
    else {
      val props = parseProperties(trimmed)
      val name = props.get("name").map(stripQuotes)
      (name, props)
    }
  }

  private def parseProperties(body: String): collection.Map[String, String] = {
    val props = Collections.newMap[String, String]
    var i = 0
    while i < body.length do {
      while i < body.length && body(i) == ' ' do i += 1
      if i >= body.length then return props
      val eq = body.indexOf('=', i)
      if eq < 0 then return props
      val key = Strings.trim(body.substring(i, eq))
      i = eq + 1
      if i < body.length && body(i) == '"' then {
        val endQuote = body.indexOf('"', i + 1)
        if endQuote >= 0 then {
          props.put(key, body.substring(i + 1, endQuote))
          i = endQuote + 1
        } else i = body.length
      } else {
        val space = body.indexOf(' ', i)
        val value = if space >= 0 then body.substring(i, space) else body.substring(i)
        props.put(key, Strings.trim(value))
        i = if space >= 0 then space + 1 else body.length
      }
    }
    props
  }

  private def stripQuotes(value: String): String = {
    val t = Strings.trim(value)
    if t.length >= 2 && t.startsWith("\"") && t.endsWith("\"") then t.substring(1, t.length - 1) else t
  }
}
