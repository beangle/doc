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

import org.beangle.doc.transfer.Format

/**
  * ItemReader interface.
  *
  * @author chaostone
  */
trait Reader {

  def readAttributes(): List[Attribute]
  /**
   * 读取数据
   */
  def read(): Any

  /**
   * 返回读取类型的格式
   */
  def format: Format

  /**当前数据的位置*/
  def location:String
  /**
   * 关闭
   */
  def close(): Unit
}
