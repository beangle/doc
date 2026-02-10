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

import com.itextpdf.kernel.pdf.{PdfDocument, PdfReader, PdfWriter}
import org.beangle.commons.io.IOs

import java.io.*

object PdfMerger {
  /** pdf合并
   *
   * @return 合并后的pdf的二进制内容
   * */
  @deprecated("using Files", "0.5.3")
  def merge(ins: Seq[InputStream], bos: OutputStream): Unit = {
    Docs.merge(ins, bos)
  }

  @deprecated("using Files", "0.5.3")
  def mergeFiles(filePaths: collection.Seq[File], target: File): Unit = {
    Docs.merge(filePaths, target)
  }
}
