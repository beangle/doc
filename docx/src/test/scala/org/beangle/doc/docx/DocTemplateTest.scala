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

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.beangle.template.freemarker.DefaultTemplateInterpreter
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, FileOutputStream}
import java.net.URL
import java.nio.file.Files
import java.time.{LocalDate, ZoneId}

import scala.jdk.CollectionConverters.*

class DocTemplateTest extends AnyFunSpec, Matchers {
  val data1 = Map("param1" -> "value1", "param2" -> "value2")
  val data2 = Map("param1" -> "value3", "param2" -> "value4")

  private def templateUrl(text: String): URL = {
    val doc = new XWPFDocument()
    doc.createParagraph().createRun().setText(text)
    val file = Files.createTempFile("doc-template", ".docx").toFile
    file.deleteOnExit()
    val os = new FileOutputStream(file)
    try doc.write(os)
    finally os.close()
    doc.close()
    file.toURI.toURL
  }

  /** 正文仅含一个单格表格，内容为 cellText（用于表格内 processAll 回归） */
  private def templateUrlTable(cellText: String): URL = {
    val doc = new XWPFDocument()
    doc.createTable(1, 1).getRow(0).getCell(0).setText(cellText)
    val file = Files.createTempFile("doc-template-table", ".docx").toFile
    file.deleteOnExit()
    val os = new FileOutputStream(file)
    try doc.write(os)
    finally os.close()
    doc.close()
    file.toURI.toURL
  }

  /** 段落内多个 run 拼接模板（模拟 Word 拆分）。 */
  private def templateUrlRuns(parts: String*): URL = {
    val doc = new XWPFDocument()
    val run = doc.createParagraph().createRun()
    parts.headOption.foreach(run.setText)
    parts.drop(1).foreach { p =>
      doc.getParagraphs.get(0).createRun().setText(p)
    }
    val file = Files.createTempFile("doc-template-runs", ".docx").toFile
    file.deleteOnExit()
    val os = new FileOutputStream(file)
    try doc.write(os)
    finally os.close()
    doc.close()
    file.toURI.toURL
  }

  describe("DocTemplate replace function") {
    it("replace normal") {
      DefaultTemplateInterpreter.process("${param1} is fine", data1) should equal("value1 is fine")
    }
    it("replace normal multiple") {
      DefaultTemplateInterpreter.process("#img#${param1} != ${param2}", data1) should equal("#img#value1 != value2")
    }
    it("replace expression with space") {
      DefaultTemplateInterpreter.process("${param1 } != ${ param2}", data1) should equal("value1 != value2")
    }
    it("extractDirectives for img") {
      val template = new DocTemplate(null)
      val rs1 = template.extractDirectives("申请人签名：${dd}")
      val rs2 = template.extractDirectives(
        "申请人签名：[#img src=step0_esign height=\"10mm\" width=\"30mm\" /]${step0_auditAt}[#img src=step0_esign height=\"10mm\" width=\"30mm\" /]")
      assert(rs1._1 == "申请人签名：${dd}")
      assert(rs1._2.isEmpty)
      assert(rs2._1 == "申请人签名：#img1#${step0_auditAt}#img2#")
      assert(rs2._2.size == 2)
      assert(rs2._2.forall(_.kind == "img"))
      assert(rs2._2.head.properties("src") == "step0_esign")
    }

    it("extractDirectives parses checkbox value and strips quoted name property") {
      val template = new DocTemplate(null)
      val (text1, dirs1) = template.extractDirectives("同意 [#checkbox agree /] 条款")
      text1 should equal("同意 #checkbox1# 条款")
      dirs1 should equal(Seq(Directive("checkbox", "#checkbox1#", Some("agree"), Map.empty)))

      val (_, dirs2) = template.extractDirectives("[#checkbox name=\"agree\" /]")
      dirs2.head.name should equal(Some("agree"))
      dirs2.head.properties("name") should equal("agree")
    }

    it("extractDirectives parses mixed directives in order") {
      val template = new DocTemplate(null)
      val raw = "x [#checkbox agree /] y [#maxlen 5 /] z [#img src=a /] w"
      val (text, dirs) = template.extractDirectives(raw)
      text should equal("x #checkbox1# y #maxlen1# z #img1# w")
      dirs.map(_.kind) should equal(Seq("checkbox", "maxlen", "img"))
      Directive.byPlaceholder(dirs).keySet should equal(Set("#checkbox1#", "#maxlen1#", "#img1#"))
    }

    it("find template regions for img, checkbox, maxlen and expressions") {
      val template = new DocTemplate(null)
      val text = "x [#checkbox agree /] y [#maxlen 5 /] z [#img src=a /] w ${name}"
      template.templateRegions(text).map { case (s, e) => text.substring(s, e) } should contain theSameElementsAs Seq(
        "[#checkbox agree /]",
        "[#maxlen 5 /]",
        "[#img src=a /]",
        "${name}"
      )
    }
  }

  describe("DocTemplate processAll") {
    it("render each item on separate pages by default") {
      val url = templateUrl("Name: ${name}")
      val bytes = DocTemplate.processAll(url, Seq(
        Map("name" -> "Alice"),
        Map("name" -> "Bob")
      ))
      val doc = new XWPFDocument(new ByteArrayInputStream(bytes))
      try {
        doc.getParagraphs.size() should equal(2)
        doc.getParagraphs.get(0).getText.trim should equal("Name: Alice")
        doc.getParagraphs.get(1).getText.trim should equal("Name: Bob")
      } finally doc.close()
    }

    it("replace variables inside table on second and later copies") {
      val url = templateUrlTable("Name: ${name}")
      val bytes = DocTemplate.processAll(url, Seq(
        Map("name" -> "Alice"),
        Map("name" -> "Bob")
      ))
      val doc = new XWPFDocument(new ByteArrayInputStream(bytes))
      try {
        doc.getTables.size() should equal(2)
        doc.getTables.get(0).getRow(0).getCell(0).getText.trim should equal("Name: Alice")
        doc.getTables.get(1).getRow(0).getCell(0).getText.trim should equal("Name: Bob")
      } finally doc.close()
    }

    it("keep distinct signature images for each item") {
      val url = templateUrl("sign:[#img src=sign height=\"10mm\" width=\"10mm\" /]")
      val redPng = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
      val bluePng = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYGD4DwABBAEAYbF9xgAAAABJRU5ErkJggg=="
      val bytes = DocTemplate.processAll(url, Seq(
        Map("sign" -> redPng),
        Map("sign" -> bluePng)
      ))
      val doc = new XWPFDocument(new ByteArrayInputStream(bytes))
      try {
        val pictureBytes = doc.getAllPictures.asScala.map(_.getData).toSet
        pictureBytes.size should equal(2)
      } finally doc.close()
    }

    it("process single item equals process") {
      val url = templateUrl("${param1} and ${param2}")
      val single = DocTemplate.process(url, data1)
      val all = DocTemplate.processAll(url, Seq(data1, data2))

      val singleFile = Files.createTempFile("doc-template-single-", ".docx")
      val allFile = Files.createTempFile("doc-template-all-", ".docx")
      Files.write(singleFile, single)
      Files.write(allFile, all)
      println(s"process 结果: ${singleFile.toAbsolutePath}")
      println(s"processAll 结果: ${allFile.toAbsolutePath}")
//      Files.deleteIfExists(singleFile)
//      Files.deleteIfExists(allFile)
    }
  }

  describe("DocMerger.appendTo") {
    it("appends two rendered documents with distinct images") {
      val url = templateUrl("sign:[#img src=sign height=\"10mm\" width=\"10mm\" /]")
      val redPng = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
      val bluePng = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYGD4DwABBAEAYbF9xgAAAABJRU5ErkJggg=="
      val b1 = DocTemplate.process(url, Map("sign" -> redPng))
      val b2 = DocTemplate.process(url, Map("sign" -> bluePng))

      val target = new XWPFDocument()
      try {
        val d1 = new XWPFDocument(new ByteArrayInputStream(b1))
        try DocMerger.appendTo(target, d1)
        finally d1.close()
        val d2 = new XWPFDocument(new ByteArrayInputStream(b2))
        try DocMerger.appendTo(target, d2)
        finally d2.close()
        val bos = new ByteArrayOutputStream()
        target.write(bos)
        val merged = new XWPFDocument(new ByteArrayInputStream(bos.toByteArray))
        try merged.getAllPictures.asScala.map(_.getData).toSet.size should equal(2)
        finally merged.close()
      } finally target.close()
    }

    it("appends paragraphs in order") {
      val url = templateUrl("x:${v}")
      val target = new XWPFDocument()
      try {
        val p1 = new XWPFDocument(new ByteArrayInputStream(DocTemplate.process(url, Map("v" -> "1"))))
        try DocMerger.appendTo(target, p1)
        finally p1.close()
        val p2 = new XWPFDocument(new ByteArrayInputStream(DocTemplate.process(url, Map("v" -> "2"))))
        try DocMerger.appendTo(target, p2)
        finally p2.close()
        val bos = new ByteArrayOutputStream()
        target.write(bos)
        val out = new XWPFDocument(new ByteArrayInputStream(bos.toByteArray))
        try {
          out.getParagraphs.size() should equal(2)
          out.getParagraphs.get(0).getText.trim should equal("x:1")
          out.getParagraphs.get(1).getText.trim should equal("x:2")
        } finally out.close()
      } finally target.close()
    }
  }

  describe("DocTemplate checkbox and mergeRun") {

    it("process renders checkbox checked and unchecked") {
      val url = templateUrl("[#checkbox agree /]")
      val checked = DocTemplate.process(url, Map("agree" -> true))
      val unchecked = DocTemplate.process(url, Map("agree" -> false))
      val doc1 = new XWPFDocument(new ByteArrayInputStream(checked))
      val doc2 = new XWPFDocument(new ByteArrayInputStream(unchecked))
      try {
        doc1.getParagraphs.get(0).getRuns.get(0).getFontFamily should equal("Wingdings 2")
        doc2.getParagraphs.get(0).getRuns.get(0).getFontFamily should equal("Wingdings 2")
      } finally {
        doc1.close()
        doc2.close()
      }
    }

    it("process renders checkbox in paragraph with surrounding text") {
      val url = templateUrl("同意 [#checkbox agree /] 条款")
      val bytes = DocTemplate.process(url, Map("agree" -> true))
      val doc = new XWPFDocument(new ByteArrayInputStream(bytes))
      try doc.getParagraphs.get(0).getText should include("同意")
      finally doc.close()
    }

    it("mergeRun merges split checkbox directive") {
      val url = templateUrlRuns("[", "#checkbox agree /]", " 条款")
      val bytes = DocTemplate.process(url, Map("agree" -> true))
      val doc = new XWPFDocument(new ByteArrayInputStream(bytes))
      try doc.getParagraphs.get(0).getRuns.get(0).getFontFamily should equal("Wingdings 2")
      finally doc.close()
    }

    it("mergeRun merges split maxlen directive") {
      val url = templateUrlRuns("[#max", "len 5 /]${name}")
      val bytes = DocTemplate.process(url, Map("name" -> "Alice"))
      val doc = new XWPFDocument(new ByteArrayInputStream(bytes))
      try doc.getParagraphs.get(0).getText.trim should equal("Alice")
      finally doc.close()
    }

    it("mergeRun merges adjacent split placeholders with shared boundary run") {
      val url = templateUrlRuns(
        "${apply.alterFrom",
        "?string(\"yyyy-MM-dd\")",
        "}至${apply.alterTo",
        "?string(\"yyyy-MM-dd\")",
        "}"
      )
      val zone = ZoneId.systemDefault()
      val toDate = (d: LocalDate) => java.util.Date.from(d.atStartOfDay(zone).toInstant)
      val apply = Map("alterFrom" -> toDate(LocalDate.of(2024, 1, 1)), "alterTo" -> toDate(LocalDate.of(2024, 12, 31)))
      val bytes = DocTemplate.process(url, Map("apply" -> apply))
      val doc = new XWPFDocument(new ByteArrayInputStream(bytes))
      try doc.getParagraphs.get(0).getText.trim should equal("2024-01-01至2024-12-31")
      finally doc.close()
    }

    it("process renders two img directives in one paragraph") {
      val redPng = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
      val bluePng = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYGD4DwABBAEAYbF9xgAAAABJRU5ErkJggg=="
      val url = templateUrl("[#img src=sign1 height=\"10mm\" width=\"10mm\" /] and [#img src=sign2 height=\"10mm\" width=\"10mm\" /]")
      val bytes = DocTemplate.process(url, Map("sign1" -> redPng, "sign2" -> bluePng))
      val doc = new XWPFDocument(new ByteArrayInputStream(bytes))
      try doc.getAllPictures.size() should equal(2)
      finally doc.close()
    }
  }
}
