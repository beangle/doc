package org.beangle.doc.pdf

import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.io.source.ByteUtils
import com.itextpdf.kernel.pdf.*
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.layout.Canvas
import com.itextpdf.layout.element.Image
import org.beangle.commons.io.{IOs, Files as IOFiles}

import java.io.*
import java.net.URL

object Docs {

  /** 旋转文档
   *
   * @param in
   * @param out
   * @param degree 正数表示顺时针旋转
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


  /** 将PDF拆分成每页一个文件的方式进行拆分
   *
   * @param input
   * @param save
   */
  def split(input: InputStream, save: Array[Byte] => Unit): Unit = {
    val srcDoc = new PdfDocument(new PdfReader(input))
    try {
      val totalPages = srcDoc.getNumberOfPages
      for (pageNum <- 1 to totalPages) {
        val bos = new ByteArrayOutputStream()
        val writer = new PdfWriter(bos)
        val targetDoc = new PdfDocument(writer)
        srcDoc.copyPagesTo(pageNum, pageNum, targetDoc)
        targetDoc.close()
        save(bos.toByteArray)
      }
    } finally {
      IOs.close(srcDoc)
    }
  }

  /** pdf合并
   *
   * @return 合并后的pdf的二进制内容
   * */
  def merge(ins: Seq[InputStream], bos: OutputStream): Unit = {
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

  /** 合并文件
   * @param filePaths
   * @param target
   */
  def merge(filePaths: scala.collection.Iterable[File], target: File): Unit = {
    val ins = filePaths.flatMap { f =>
      if (f.exists()) {
        if (f.length() == 0) {
          PdfLogger.info(s"ignore empty file ${f.getAbsolutePath}")
          None
        } else if (f.getAbsolutePath.endsWith(".pdf") || f.getAbsolutePath.endsWith(".PDF")) {
          Some(new FileInputStream(f))
        } else {
          PdfLogger.info(s"illegal pdf file ${f.getAbsolutePath}")
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

  /** PDF文件加密
   *
   * @param pdf           pdf file
   * @param userPassword  打开密码
   * @param ownerPassword 修改密码
   * @param permission    未使用修改密码是，可以使用的权限
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

  /** 添加图片
   *
   * @param inputDoc
   * @param outputDoc
   * @param imageUrl
   * @param location
   * @param size
   */
  def addImage(inputDoc: File, outputDoc: File, imageUrl: URL,
               location: (Float, Float), size: (Float, Float)): Unit = {
    val newDoc = new PdfDocument(new PdfReader(new FileInputStream(inputDoc)),
      new PdfWriter(new FileOutputStream(outputDoc)))

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
