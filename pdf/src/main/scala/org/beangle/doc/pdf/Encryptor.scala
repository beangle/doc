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

import com.itextpdf.io.source.ByteUtils
import com.itextpdf.kernel.pdf.*
import org.beangle.commons.io.Files

import java.io.{File, FileOutputStream}

object Encryptor {

  def encrypt(pdf: File, userPassword: Option[String], ownerPassword: String, permission: Int = EncryptionConstants.ALLOW_PRINTING): Unit = {
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
    Files.copy(encrypted, pdf)
    encrypted.delete()
  }
}
