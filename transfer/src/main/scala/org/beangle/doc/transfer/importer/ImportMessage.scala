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

package org.beangle.doc.transfer.importer

import org.beangle.commons.lang.Objects

import scala.collection.mutable.ListBuffer

object ImporterMessage {
  /** Constant <code>ERROR_ATTRS="error.transfer.attrs"</code> */
  val ERROR_ATTRS = "error.transfer.attrs"

  /** Constant <code>ERROR_ATTRS_EXPORT="error.transfer.attrs.export"</code> */
  val ERROR_ATTRS_EXPORT = "error.transfer.attrs.export"
}

/**
 * 转换消息
 */
class ImportMessage(val index: Int, val location: String, val message: String, value: Any) {

  /**
   * 消息中使用的对应值
   */
  val values = new ListBuffer[Any]
  values += value

  /**
   * toString.
   */
  override def toString: String = {
    Objects.toStringBuilder(this).add("index", this.index).add("message", this.message)
      .add("values", this.values).toString()
  }

}
