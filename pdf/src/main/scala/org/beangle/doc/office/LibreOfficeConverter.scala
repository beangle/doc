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

package org.beangle.doc.office

import org.beangle.commons.bean.{Disposable, Initializing}
import org.jodconverter.core.document.DefaultDocumentFormatRegistry
import org.jodconverter.core.office.InstalledOfficeManagerHolder
import org.jodconverter.local.LocalConverter

import java.io.File

class LibreOfficeConverter extends Initializing, Disposable {
  var processCount: Int = 1

  override def init(): Unit = {
    val manager = LibreOfficeLauncher().launch(processCount)
    InstalledOfficeManagerHolder.setInstance(manager)
  }

  def convertToPdf(input: File, output: File): Unit = {
    val builder = LocalConverter.builder()
    builder.build.convert(input).to(output).as(DefaultDocumentFormatRegistry.PDF).execute()
  }

  override def destroy(): Unit = {
    val manager = InstalledOfficeManagerHolder.getInstance()
    if (null != manager) {
      manager.stop()
    }
  }

}
