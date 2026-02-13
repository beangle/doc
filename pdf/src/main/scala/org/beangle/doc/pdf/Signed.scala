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

import com.itextpdf.forms.PdfAcroForm
import com.itextpdf.kernel.pdf.*
import com.itextpdf.signatures.{PdfSignature, SignatureUtil}

import java.io.*
import java.time.Instant
import scala.jdk.CollectionConverters.*

object Signed {

  /** 检测 PDF 文档是否包含电子签名
   *
   * @param input PDF 文件
   * @return 若包含签名字段、签名注释或签名元数据则返回 true
   */
  def hasSignature(input: File): Boolean = {
    if (!input.exists() || input.isDirectory) return false
    val reader = new PdfReader(input)
    val pdfDoc = new PdfDocument(reader)
    try {
      val catalog = pdfDoc.getCatalog.getPdfObject
      val hasCatalogSig = catalog.containsKey(PdfName.DocMDP) || catalog.containsKey(PdfName.SigRef) ||
        (catalog.containsKey(PdfName.AcroForm) && catalog.getAsDictionary(PdfName.AcroForm).containsKey(PdfName.SigFlags))

      val acroForm = PdfAcroForm.getAcroForm(pdfDoc, false)
      val hasFormSigField = acroForm != null && acroForm.getAllFormFields.asScala.exists {
        case (_, field) => PdfName.Sig == field.getFormType
      }

      val hasPageSigAnnot = (1 to pdfDoc.getNumberOfPages).exists { pageNum =>
        val annotations = pdfDoc.getPage(pageNum).getAnnotations
        annotations != null && !annotations.isEmpty && annotations.asScala.exists { annot =>
          val subtype = annot.getSubtype
          subtype != null && (subtype == PdfName.Widget || subtype == PdfName.Stamp)
        }
      }

      hasCatalogSig || hasFormSigField || hasPageSigAnnot
    } finally {
      pdfDoc.close()
      reader.close()
    }
  }

  /** 获取 PDF 文档中所有签名的详细信息
   *
   * @param input PDF 文件
   * @return 各签名的详细信息（签名字段名、签名时间、签名者、原因、位置、联系信息、证书主题）
   */
  def getSignatureInfos(input: File): Seq[SignatureInfo] = {
    if (!input.exists() || input.isDirectory) return Seq.empty
    val reader = new PdfReader(input)
    val pdfDoc = new PdfDocument(reader)
    try {
      val sigUtil = new SignatureUtil(pdfDoc)
      val names = sigUtil.getSignatureNames.asScala
      names.flatMap { name =>
        try {
          val pkcs7 = sigUtil.readSignatureData(name)
          val date = Option(pkcs7.getTimeStampDate).getOrElse(pkcs7.getSignDate)
          val signTime = Option(date).map(_.toInstant).getOrElse(Instant.EPOCH)

          val sigDict = sigUtil.getSignatureDictionary(name)
          val pdfSig = Option(sigDict).map(new PdfSignature(_))
          val signerName = pdfSig.flatMap(s => Option(s.getName)).filter(_.nonEmpty)
          val reason = pdfSig.flatMap(s => Option(s.getReason)).filter(_.nonEmpty)
          val location = pdfSig.flatMap(s => Option(s.getLocation)).filter(_.nonEmpty)
          val contactInfo = Option(sigDict)
            .filter(_.containsKey(PdfName.ContactInfo))
            .flatMap(d => Option(d.getAsString(PdfName.ContactInfo)))
            .map(_.toUnicodeString)
            .filter(_.nonEmpty)
          val certificateSubject = Option(pkcs7.getSigningCertificate)
            .map(_.getSubjectX500Principal.getName)

          Some(SignatureInfo(
            fieldName = name,
            signTime = signTime,
            signerName = signerName,
            reason = reason,
            location = location,
            contactInfo = contactInfo,
            certificateSubject = certificateSubject
          ))
        } catch {
          case _: Exception => None
        }
      }.toSeq
    } finally {
      pdfDoc.close()
      reader.close()
    }
  }

  /** 获取 PDF 文档中所有签名的 signing 时间
   *
   * @param input PDF 文件
   * @return 各签名的 signing 时间，优先使用时间戳（若存在），否则使用 signing 证书中的时间
   */
  def getSignatureTimes(input: File): Seq[Instant] = {
    if (!input.exists() || input.isDirectory) return Seq.empty
    val reader = new PdfReader(input)
    val pdfDoc = new PdfDocument(reader)
    try {
      val sigUtil = new SignatureUtil(pdfDoc)
      val names = sigUtil.getSignatureNames.asScala
      names.flatMap { name =>
        try {
          val pkcs7 = sigUtil.readSignatureData(name)
          val date = Option(pkcs7.getTimeStampDate).getOrElse(pkcs7.getSignDate)
          Option(date).map(_.toInstant)
        } catch {
          case _: Exception => None
        }
      }.toSeq
    } finally {
      pdfDoc.close()
      reader.close()
    }
  }

  /** 移除 PDF 文档中所有签名（含签名字段与签名注释）
   *
   * @param input 待处理的 PDF 文件，结果将覆盖原文件
   */
  def removeAllSignatures(input: File): Unit = {
    val baos = new ByteArrayOutputStream()
    val reader = new PdfReader(input)
    val tempWriter = new PdfWriter(baos)
    val pdfDoc = new PdfDocument(reader, tempWriter)

    try {
      // 删除签名字段
      val acroForm = PdfAcroForm.getAcroForm(pdfDoc, false)
      if (acroForm != null) {
        val signatureFieldNames = acroForm.getAllFormFields.asScala
          .filter { case (_, field) => PdfName.Sig == field.getFormType }
          .keys
          .toList
        signatureFieldNames.foreach(acroForm.removeField)
        acroForm.flattenFields()
      }

      // 删除签名注释
      for (pageNum <- 1 to pdfDoc.getNumberOfPages) {
        val page = pdfDoc.getPage(pageNum)
        val annotations = page.getAnnotations
        if (annotations != null && !annotations.isEmpty) {
          val annotationsToRemove = annotations.asScala.filter { annot =>
            val subtype = annot.getSubtype
            subtype != null && (subtype == PdfName.Widget || subtype == PdfName.Stamp)
          }.toList
          annotationsToRemove.foreach(page.removeAnnotation)
        }
      }

      // 清除根层级签名信息
      val catalog = pdfDoc.getCatalog.getPdfObject
      if (catalog.containsKey(PdfName.AcroForm)) {
        val acroFormDict = catalog.getAsDictionary(PdfName.AcroForm)
        acroFormDict.remove(PdfName.SigFlags)
      }
      catalog.remove(PdfName.DocMDP)
      catalog.remove(PdfName.SigRef)

    } finally {
      pdfDoc.close()
      reader.close()
      tempWriter.close()
    }

    // 覆盖原文件
    val fos = new FileOutputStream(input)
    try {
      baos.writeTo(fos)
    } finally {
      fos.close()
      baos.close()
    }
  }

  /** 签名详细信息
   *
   * @param fieldName          签名字段名
   * @param signTime           签名时间
   * @param signerName         签名者姓名（来自 /Name，可为空）
   * @param reason             签名原因（为何签，可为空）
   * @param location           签名位置（可为空）
   * @param contactInfo        联系信息（可为空）
   * @param certificateSubject 证书主题 DN（来自 signing 证书，可为空）
   */
  case class SignatureInfo(
                            fieldName: String,
                            signTime: Instant,
                            signerName: Option[String] = None,
                            reason: Option[String] = None,
                            location: Option[String] = None,
                            contactInfo: Option[String] = None,
                            certificateSubject: Option[String] = None
                          )
}
