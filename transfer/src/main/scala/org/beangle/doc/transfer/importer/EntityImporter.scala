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
import org.beangle.data.model.Entity
import org.beangle.data.model.meta.{Domain, EntityType}
import org.beangle.data.model.util.Populator
import org.beangle.doc.transfer.IllegalFormatException

/**
 * EntityImporter interface.
 *
 * @author chaostone
 */
trait EntityImporter extends Importer {

  var populator: Populator = _

  var domain: Domain = _

}

object EntityPrepare extends ImportPrepare {

  def prepare(importer: Importer): Unit = {
    val i = importer.asInstanceOf[DefaultEntityImporter]
    i.addEntity(i.shortName, i.entityClass)

    val reader = importer.reader
    importer.asInstanceOf[AbstractImporter].setAttrs(reader.readAttributes())
  }

}

/**
 * MultiEntityImporter class.
 *
 * @author chaostone
 */
class MultiEntityImporter extends AbstractImporter with EntityImporter with Logging {

  protected var currents = new collection.mutable.HashMap[String, AnyRef]

  // [alias,entityType]
  protected val entityTypes = new collection.mutable.HashMap[String, EntityType]

  def aliases: Iterable[String] = entityTypes.keys

  /**
   * 摘取指定前缀的参数
   */
  private def sub(data: collection.Map[String, Any], alias: String): collection.mutable.Map[String, Any] = {
    val prefix = alias + "."
    val newParams = new collection.mutable.HashMap[String, Any]
    for ((key, value) <- data) {
      if (key.indexOf(prefix) == 0) {
        newParams.put(key.substring(prefix.length), value)
      }
    }
    newParams
  }

  /**
   * transferItem.
   */
  override def transferItem(): Unit = {
    entityTypes foreach {
      case (name, etype) =>
        val entity = getCurrent(name)
        sub(curData, name) foreach { entry =>
          var value = entry._2
          // 处理空字符串并对所有的字符串进行trim
          value match {
            case s: String => value = if Strings.isBlank(s) then null else Strings.trim(s)
            case _ =>
          }
          // 处理null值
          if (null != value) {
            if (value.equals("null")) value = null
            populateValue(entity.asInstanceOf[Entity[_]], etype, entry._1, value)
          }
        }
    }

  }

  /**
   * Populate single attribute
   */
  protected def populateValue(entity: Entity[_], etype: EntityType, attr: String, value: Any): Unit = {
    // 当有深层次属性,这里和传统的Populate不一样，导入面向的使用户，属性可能出现foreigner.name之类的，在正常的form表单中不会出现
    if (Strings.contains(attr, '.')) {
      val parentPath = Strings.substringBeforeLast(attr, ".")
      val propertyType = populator.init(entity, etype, parentPath)
      val property = propertyType._1
      property match {
        case e: Entity[_] =>
          if (e.persisted) {
            populator.populate(entity, etype, parentPath, null)
            populator.init(entity, etype, parentPath)
          }
        case _ =>
      }
    }

    if (!populator.populate(entity, etype, attr, value)) {
      result.addFailure(description(attr) + " data format error.", value)
    }
  }

  protected def getEntityClass(alias: String): Class[_] = {
    getEntityType(alias).clazz
  }

  protected def getEntityType(alias: String): EntityType = {
    entityTypes(alias)
  }

  def addEntity(clazz: Class[_]): Unit = {
    val shortName = Strings.uncapitalize(Strings.substringAfterLast(clazz.getName, "."))
    this.addEntity(shortName, clazz)
  }

  def addEntity(alias: String, entityClass: Class[_]): Unit = {
    domain.getEntity(entityClass) match {
      case Some(entityType) => entityTypes.put(alias, entityType)
      case None => throw new RuntimeException("cannot find entity type for " + entityClass)
    }
  }

  def addEntity(alias: String, entityName: String): Unit = {
    domain.getEntity(entityName) match {
      case Some(entityType) => entityTypes.put(alias, entityType)
      case None => throw new RuntimeException("cannot find entity type for " + entityName)
    }
  }

  protected def getEntityName(attr: String): String = {
    getEntityType(attr).entityName
  }

  def getCurrent(alias: String): AnyRef = {
    var entity = currents.get(alias).orNull
    if (null == entity) {
      entityTypes.get(alias) match {
        case Some(entityType) =>
          entity = entityType.newInstance()
          currents.put(alias, entity)
        case None =>
          logger.error(s"Not register entity type for $alias")
          throw new IllegalFormatException("Not register entity type for " + alias, null)
      }
    }
    entity
  }

  override def dataName: String = {
    "multi entity"
  }

  override def current_=(obj: AnyRef): Unit = {
    currents = obj.asInstanceOf[collection.mutable.HashMap[String, AnyRef]]
  }

  override def current: AnyRef = {
    currents
  }

  protected override def beforeImportItem(): Unit = {
    this.currents.clear()
  }
}

class DefaultEntityImporter(val entityClass: Class[_], val shortName: String) extends MultiEntityImporter {

  this.prepare = EntityPrepare

  protected override def getEntityType(attr: String): EntityType = {
    entityTypes(shortName)
  }

  def getEntityClass: Class[_] = {
    entityTypes(shortName).clazz
  }

  def getEntityName: String = {
    entityTypes(shortName).entityName
  }

  override def getCurrent(alias: String): AnyRef = {
    current
  }

  override def current: AnyRef = {
    super.getCurrent(shortName)
  }

  protected override def getEntityName(alias: String): String = {
    getEntityName
  }

  override def current_=(obj: AnyRef): Unit = {
    currents.put(shortName, obj)
  }

}
