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

package org.beangle.doc.excel.template.directive

import org.beangle.commons.bean.Properties
import org.beangle.doc.excel.template.Area
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentHashMap

object DirectiveFactory {
  val logger = LoggerFactory.getLogger(DirectiveFactory.getClass)

  def newDirective(name: String, attrs: collection.Map[String, String], areas: Iterable[Area]): Option[Directive] = {
    try {
      val result = name match {
        case "if" =>
          check(name, attrs, areas, 1, 2, "condition")
          new IfDirective(attrs("condition"), areas.head, if (areas.tail.isEmpty) Area.Empty else areas.tail.head)
        case "each" =>
          check(name, attrs, areas, 1, 1, "var", "items")
          new EachDirective(attrs("var"), attrs("items"), areas.head)
        case "area" => new AreaDirective
        case "image" =>
          check(name, attrs, areas, 1, 1, "src")
          new ImageDirective(attrs("src"), attrs.getOrElse("imageType", "PNG"), areas.head)
        case "updateCell" =>
          check(name, attrs, areas, 1, 1)
          new UpdateCellDirective(areas.head)
        case "mergeCells" =>
          check(name, attrs, areas, 1, 1)
          new MergeCellsDirective(areas.head)
      }
      attrs foreach { case (k, v) =>
        if (Properties.isWriteable(result, k)) Properties.copy(result, k, v)
      }
      Some(result)
    } catch {
      case e: Throwable => None
    }
  }

  private def check(cmd: String, attrMap: collection.Map[String, String], areas: Iterable[Area],
                    minAreaCnt: Int, maxAreaCnt: Int, properties: String*): Unit = {
    val areaCnt = areas.size
    if (areaCnt < minAreaCnt || areaCnt > maxAreaCnt) {
      throw new RuntimeException(s"${cmd} need [${minAreaCnt}~${maxAreaCnt}] areas but ${areaCnt} specified.")
    } else {
      val missing = properties.filter(!attrMap.contains(_))
      if (missing.nonEmpty) {
        throw new RuntimeException(s"${cmd} require properties ${missing} ")
      }
    }
  }
}
