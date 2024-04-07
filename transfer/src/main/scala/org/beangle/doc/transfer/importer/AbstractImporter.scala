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

import org.beangle.commons.lang.Strings
import org.beangle.commons.logging.Logging
import org.beangle.doc.transfer.Format

import java.util.Locale
import scala.collection.mutable.ListBuffer

/** 导入的抽象和缺省实现
 *
 * @author chaostone
 */
abstract class AbstractImporter extends Importer with Logging {
  protected var result: ImportResult = _
  protected val listeners = new ListBuffer[ImportListener]
  var success, fail = 0
  this.prepare = new AttributePrepare()
  var index = 0

  /**
   * 进行转换
   */
  def transfer(tr: ImportResult): Unit = {
    this.result = tr
    this.result.transfer = this
    val transferStartAt = System.currentTimeMillis()
    try {
      prepare.prepare(this)
      listeners.foreach(l => l.onStart(tr))
      var stopped = false
      while (!stopped && read()) {
        index += 1
        try {
          beforeImportItem()
          if (isDataValid) {
            val errors = tr.errors
            for (l <- listeners; if tr.errors == errors) l.onItemStart(tr)
            if (tr.errors == errors) { // 如果转换前已经存在错误,则不进行转换
              transferItem()
              for (l <- listeners; if tr.errors == errors) l.onItemFinish(tr)
              if tr.errors == errors then this.success += 1 else this.fail += 1
            }
          }
        } catch {
          case e: Throwable =>
            logger.error(e.getMessage, e)
            if stopOnError then
              stopped = true
              tr.addFailure("导入异常,剩余数据停止导入", e.getMessage)
            else
              tr.addFailure("导入异常", e.getMessage)
            this.fail += 1
        }
      }
      listeners.foreach(l => l.onFinish(tr))
      reader.close()
    } catch {
      case e: Throwable => tr.addFailure("导入异常", e.getMessage)
    }
    logger.debug("importer elapse: " + (System.currentTimeMillis() - transferStartAt))
  }

  override def ignoreNull: Boolean = true

  override def locale: Locale = Locale.getDefault()

  override def format: Format = reader.format

  override def dataLocation: String = if null != reader then reader.location else "-1"

  override def addListener(listener: ImportListener): Importer = {
    listeners += listener
    listener.transfer = this
    this
  }

  protected def beforeImportItem(): Unit = {
  }

  /**
   * 改变现有某个属性的值
   */
  def changeCurValue(attr: String, value: Any): Unit = {
    this.curData.put(attr, value)
  }

  final override def read(): Boolean = {
    val data = reader.read().asInstanceOf[Array[_]]
    if (null == data) {
      this.current = null
      this.curData = null
      false
    } else {
      curData = new collection.mutable.HashMap[String, Any]
      data.indices foreach { i =>
        val di = data(i)
        di match
          case null => //ignore
          case a: String => if (Strings.isNotBlank(a)) this.curData.put(attrs(i).name, a)
          case _ => this.curData.put(attrs(i).name, di)
      }
      true
    }
  }

  override def isDataValid: Boolean = {
    this.curData.values exists { v =>
      v match {
        case tt: String => Strings.isNotBlank(tt)
        case _ => null != v
      }
    }
  }

  def setAttrs(attrs: List[Attribute]): Unit = {
    this.attrs = attrs.toArray
  }

}

class AttributePrepare extends ImportPrepare {

  def prepare(importer: Importer) : Unit = {
    val reader = importer.reader
    importer.asInstanceOf[AbstractImporter].setAttrs(reader.readAttributes())
  }

}
