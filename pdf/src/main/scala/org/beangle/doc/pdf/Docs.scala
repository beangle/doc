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

import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.io.source.ByteUtils
import com.itextpdf.kernel.pdf.*
import com.itextpdf.kernel.pdf.annot.PdfAnnotation
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.layout.Canvas
import com.itextpdf.layout.element.Image
import org.beangle.commons.io.{IOs, Files as IOFiles}

import java.io.*
import java.net.URL

object Docs {

  /** 旋转 PDF 文档页面
   *
   * @param in     源 PDF 文件
   * @param out    输出 PDF 文件
   * @param degree 旋转角度，正数表示顺时针
   */
  def rotate(in: File, out: File, degree: Int): Unit = {
    val originDoc = new PdfDocument(new PdfReader(in))
    val writer = new PdfWriter(out)
    val newDoc = new PdfDocument(writer)
    val pageCount = originDoc.getNumberOfPages
    originDoc.copyPagesTo(1, pageCount, newDoc)
    var i = 1
    while (i <= pageCount) {
      val page = newDoc.getPage(i)
      page.setRotation(degree)
      i += 1
    }
    originDoc.close()
    newDoc.close()
    writer.close()
  }

  /** 将 PDF 按页拆分，每页单独输出
   *
   * @param in     源 PDF 文件
   * @param action 对每页字节数据的回调处理
   */
  def split(in: File)(action: Array[Byte] => Unit): Unit = {
    val srcDoc = new PdfDocument(new PdfReader(in))
    try {
      val totalPages = srcDoc.getNumberOfPages
      for (pageNum <- 1 to totalPages) {
        val bos = new ByteArrayOutputStream()
        val writer = new PdfWriter(bos)
        val targetDoc = new PdfDocument(writer)
        srcDoc.copyPagesTo(pageNum, pageNum, targetDoc)
        targetDoc.close()
        action(bos.toByteArray)
      }
    } finally {
      IOs.close(srcDoc)
    }
  }

  /** 将多个 PDF 输入流合并写入指定输出流
   *
   * @param ins 待合并的 PDF 输入流序列
   * @param bos 合并结果写入的输出流
   */
  private def merge(ins: Seq[InputStream], bos: OutputStream): Unit = {
    // 创建一个新的PDF
    val writer = new PdfWriter(bos)
    val document = new PdfDocument(writer)
    ins foreach { is =>
      val bytes = IOs.readBytes(is)
      if (bytes.length > 0) {
        val reader = new PdfReader(new ByteArrayInputStream(bytes))
        val originDoc = new PdfDocument(reader)
        val pageCount = originDoc.getNumberOfPages
        val pages = originDoc.copyPagesTo(1, pageCount, document).iterator()
        while (pages.hasNext) {
          pages.next().flush()
        }
        originDoc.close()
      }
    }
    document.close()
  }

  /** 合并多个 PDF 文件为单个文件
   *
   * @param files  待合并的 PDF 文件集合
   * @param target 合并后的目标文件
   */
  def merge(files: scala.collection.Iterable[File], target: File): Unit = {
    val ins = files.flatMap { f =>
      if (f.exists()) {
        if (f.length() == 0) {
          Logger.info(s"ignore empty file ${f.getAbsolutePath}")
          None
        } else if (f.getAbsolutePath.endsWith(".pdf") || f.getAbsolutePath.endsWith(".PDF")) {
          Some(new FileInputStream(f))
        } else {
          Logger.info(s"illegal pdf file ${f.getAbsolutePath}")
          None
        }
      } else None
    }.toSeq
    val part = new File(target.getAbsolutePath + ".part")
    var os: OutputStream = null
    try {
      os = new FileOutputStream(part)
      merge(ins, os)
      os.close()
      if target.exists() then target.delete()
      part.renameTo(target)
    } finally {
      IOs.close(os)
      if (part.exists()) part.delete()
    }
  }

  /** 对 PDF 文件进行加密
   *
   * @param pdf            源 PDF 文件
   * @param userPassword   打开密码
   * @param ownerPassword  修改密码
   * @param permission     未使用修改密码时可使用的权限
   */
  def encrypt(pdf: File, userPassword: Option[String], ownerPassword: String,
              permission: Int = EncryptionConstants.ALLOW_PRINTING): Unit = {
    if (!pdf.exists() || pdf.isDirectory) return

    val reader = new PdfReader(pdf)
    reader.setCloseStream(true)
    val encrypted = File.createTempFile("encrypt", ".pdf")
    val properties = new EncryptionProperties
    properties.setStandardEncryption(ByteUtils.getIsoBytes(userPassword.orNull),
      ByteUtils.getIsoBytes(ownerPassword), permission, EncryptionConstants.STANDARD_ENCRYPTION_128)

    val os = new FileOutputStream(encrypted)
    PdfEncryptor.encrypt(reader, os, properties)
    os.close()
    reader.close()
    IOFiles.copy(encrypted, pdf)
    encrypted.delete()
  }

  /** 向 PDF 首页添加图片
   *
   * @param in       源 PDF 文件
   * @param out      输出 PDF 文件
   * @param imageUrl 图片 URL
   * @param location 图片位置坐标 (x, y)
   * @param size     图片尺寸 (宽, 高)
   */
  def addImage(in: File, out: File, imageUrl: URL,
               location: (Float, Float), size: (Float, Float)): Unit = {
    val newDoc = new PdfDocument(new PdfReader(new FileInputStream(in)),
      new PdfWriter(new FileOutputStream(out)))

    val firstPage = newDoc.getPage(1)
    val width = firstPage.getPageSize.getWidth
    val height = firstPage.getPageSize.getHeight

    val imageData = ImageDataFactory.create(imageUrl)
    val stampImage = new Image(imageData).setWidth(size._1).setHeight(size._2)

    val pdfCanvas = new PdfCanvas(firstPage)
    val canvas = new Canvas(pdfCanvas, firstPage.getPageSize)
    stampImage.setFixedPosition(location._1, location._2)
    canvas.add(stampImage)
    canvas.close()
    pdfCanvas.release()
    newDoc.close()
  }
}
