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

package org.beangle.doc.transfer.importer.listener

import org.beangle.doc.transfer.importer.{ImportListener, ImportResult}

/** 转换调试监听器
 * @author chaostone
 */
class DebugListener extends ImportListener {

  override def onStart(tr: ImportResult): Unit = {
    tr.addMessage("start", transfer.dataName)
  }

  override def onFinish(tr: ImportResult): Unit = {
    tr.addMessage("end", transfer.dataName)
  }

  override def onItemStart(tr: ImportResult): Unit = {
    tr.addMessage("start Item", transfer.index.toString)
  }

  override def onItemFinish(tr: ImportResult): Unit = {
    tr.addMessage("end Item", transfer.current)
  }

}
