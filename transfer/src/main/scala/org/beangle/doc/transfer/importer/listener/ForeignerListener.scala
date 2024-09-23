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

import org.beangle.commons.bean.Properties
import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.Strings.*
import org.beangle.data.dao.{EntityDao, OqlBuilder}
import org.beangle.data.model.Entity
import org.beangle.doc.transfer.importer.{AbstractImporter, ImportListener, ImportResult, MultiEntityImporter}

import scala.collection.mutable

object ForeignerListener {
  val CACHE_SIZE = 500
}

/** 导入数据外键监听器
 * 这里尽量使用entityDao，因为在使用entityService加载其他代码时，jpa会保存还未修改外的"半成对象"<br>
 * 从而造成有些外键是空对象的错误<br>
 * 如果外键不存在，则目标中的外键会置成null；<br>
 * 如果外键是空的，那么目标的外键取决于transfer.isIgnoreNull取值
 *
 * @author chaostone
 */
class ForeignerListener(entityDao: EntityDao) extends ImportListener {

  import ForeignerListener.*

  protected val foreignersMap = new mutable.HashMap[String, mutable.HashMap[String, Object]]

  private val foreignerKeys = new mutable.ListBuffer[String]
  foreignerKeys += "code"

  /** 说明某个外键实体，应该限定的范围 */
  private val scopes = Collections.newMap[Class[_], Map[String, Any]]

  private var multiEntity = false

  private var aliases: Set[String] = Set.empty

  private val ignores = Collections.newSet[String]

  private var keyAttrs: Set[String] = _

  override def onStart(tr: ImportResult): Unit = {
    transfer match {
      case mei: MultiEntityImporter =>
        multiEntity = true
        aliases = mei.aliases.toSet
      case _ => multiEntity = false
    }
    // 过滤所有外键
    val keyNames = Collections.newSet[String]
    transfer.attrs foreach { attr =>
      val attrName = attr.name
      if (!ignores.contains(attrName)) {
        val isForeigner =
          foreignerKeys exists { fk =>
            val endWithKey = attrName.endsWith("." + fk) && count(attrName, '.') >= 2
            if endWithKey then aliases.contains(substringBefore(attrName, ".")) else false
          }
        if isForeigner then keyNames += attrName
      }
    }
    keyAttrs = keyNames.toSet
  }

  override def onItemFinish(tr: ImportResult): Unit = {
    val iter = keyAttrs.iterator
    while (iter.hasNext) {
      val attri = iter.next()
      val codeStr = transfer.curData.getOrElse(attri, "").asInstanceOf[String]
      var foreigner: Object = null
      // 外键的代码不是空的
      if (isNotEmpty(codeStr)) {
        var entity: Object = null
        if (multiEntity) {
          val shortName = substringBefore(attri, ".")
          entity = transfer.asInstanceOf[MultiEntityImporter].getCurrent(shortName)
        } else {
          entity = transfer.current
        }

        val attr = substringAfter(attri, ".")
        var nestedForeigner = Properties.get[Object](entity, substring(attr, 0, attr.lastIndexOf(".")))
        if (nestedForeigner.isInstanceOf[Option[_]]) {
          nestedForeigner = nestedForeigner.asInstanceOf[Option[AnyRef]].orNull
        }
        nestedForeigner match {
          case _: Entity[_] =>
            val className = nestedForeigner.getClass.getName
            val foreignerMap = foreignersMap.getOrElseUpdate(className, new collection.mutable.HashMap[String, Object])
            if (foreignerMap.size > CACHE_SIZE) foreignerMap.clear()
            foreigner = foreignerMap.get(codeStr).orNull
            if (foreigner == null) {
              val clazz = nestedForeigner.getClass.asInstanceOf[Class[Entity[_]]]
              val foreigners = fetchForeigners(clazz, codeStr)
              if (foreigners.nonEmpty && foreigners.size == 1) {
                foreigner = foreigners.head
                foreignerMap.put(codeStr, foreigner)
              } else {
                if foreigners.isEmpty then tr.addFailure(transfer.description(attri) + "代码不存在", codeStr)
                else tr.addFailure(transfer.description(attri) + "代码不唯一", codeStr)
              }
            }
          case _ =>
        }
        val parentAttr = substring(attr, 0, attr.lastIndexOf("."))
        val entityImporter = transfer.asInstanceOf[MultiEntityImporter]
        entityImporter.populator.populate(entity.asInstanceOf[Entity[_]], entityImporter.domain.getEntity(entity.getClass).get, parentAttr, foreigner)
      }
    }
  }

  private def fetchForeigners(clazz: Class[Entity[_]], codeStr: String): Seq[Entity[_]] = {
    val query = OqlBuilder.from(clazz, "f")
    scopes.get(clazz) foreach { params =>
      var i = 1
      params foreach { case (k, v) =>
        query.where(s"f.${k} = :p${i}", v)
        i += 1
      }
    }
    query.where(foreignerKeys.map(k => s"f.$k = :fk_value").mkString(" or "), codeStr)
    val foreigners = entityDao.search(query)
    if (foreigners.isEmpty && codeStr.contains(' ')) {
      val codeValue = substringBefore(codeStr, " ")
      val query = OqlBuilder.from(clazz, "f")
      scopes.get(clazz) foreach { params =>
        var i = 1
        params foreach { case (k, v) =>
          query.where(s"f.${k} = :p${i}", v)
          i += 1
        }
      }
      query.where(foreignerKeys.map(k => s"f.$k = :fk_value").mkString(" or "), codeValue)
      entityDao.search(query)
    } else foreigners
  }

  def addForeigerKey(key: String): Unit = {
    this.foreignerKeys += key
  }

  def ignore(names: String*): Unit = {
    ignores ++= names
  }

  def addScope(clazz: Class[_], params: Map[String, Any]): Unit = {
    scopes.put(clazz, params)
  }
}
