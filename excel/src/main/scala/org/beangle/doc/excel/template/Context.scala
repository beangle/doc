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

package org.beangle.doc.excel.template

import org.beangle.commons.script.ExpressionEvaluator
import org.beangle.commons.script.JSR223ExpressionEvaluator
import scala.collection.mutable
/**
 * Map bean context
 */
class Context {
  protected var varMap = new mutable.HashMap[String, Any]
  var evaluator = new JSR223ExpressionEvaluator("jexl3")

  /**
   * Evaluates if the passed condition is true
   */
  def isTrue(condition: String): Boolean = {
    evaluator.eval(condition, toMap) match {
      case jb: java.lang.Boolean => jb
      case _ => throw new RuntimeException("Condition result is not a boolean value - " + condition)
    }
  }

  def toMap:collection.Map[String,Any]={
    varMap
  }

  def this(data: collection.Map[String, Any]) = {
    this()
    varMap ++= data
  }

  def getVar(name: String): Any = varMap.get(name).orNull

  def putVar(name: String, value: Any): Unit = {
    varMap.put(name, value)
  }

  def removeVar(`var`: String): Unit = {
    varMap.remove(`var`)
  }

}
