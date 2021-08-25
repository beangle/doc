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

import java.io.{File, FileOutputStream}

import com.itextpdf.text.DocWriter
import com.itextpdf.text.pdf.{PdfReader, PdfStamper, PdfWriter}
import org.beangle.commons.io.Files

object Encryptor {

  def encrypt(pdf: File, userPasswod: Option[String], ownerPassword: String, permission: Int): Unit = {
    if (!pdf.exists()||pdf.isDirectory) return
    val pdfReader = new PdfReader(pdf.toURI.toURL)
    val pdf2 = File.createTempFile("encrypt", ".pdf")
    val stamper = new PdfStamper(pdfReader, new FileOutputStream(pdf2))
    stamper.setEncryption(DocWriter.getISOBytes(userPasswod.orNull),
      DocWriter.getISOBytes(ownerPassword),
      permission,
      PdfWriter.STANDARD_ENCRYPTION_128)
    stamper.close()
    pdfReader.close()
    Files.copy(pdf2, pdf)
    pdf2.delete()
  }
}
