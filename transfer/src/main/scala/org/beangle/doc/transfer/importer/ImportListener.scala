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

/**
 * 转换监听器
 * @author chaostone
 */
trait ImportListener {

  /**
   * 开始转换
   */
  def onStart(tr: ImportResult): Unit = {}

  /**
   * 结束转换
   */
  def onFinish(tr: ImportResult): Unit = {}

  /**
   * 开始转换单个项目
   */
  def onItemStart(tr: ImportResult): Unit = {}

  /**
   * 结束转换单个项目
   */
  def onItemFinish(tr: ImportResult): Unit = {}

  /**
   * 设置转换器
   */
  var transfer: Importer = _
}
