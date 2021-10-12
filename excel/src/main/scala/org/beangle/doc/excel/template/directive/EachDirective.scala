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
import org.beangle.commons.bean.orderings.{MultiPropertyOrdering, PropertyOrdering}
import org.beangle.commons.script.ExpressionEvaluator
import org.beangle.doc.excel.template.*
import org.beangle.doc.excel.template.directive.EachDirective.*
import org.beangle.doc.excel.{CellRef, Size}
import org.slf4j.LoggerFactory

import java.util.Collection
import scala.collection.mutable

object EachDirective {
  val logger = LoggerFactory.getLogger(classOf[EachDirective])

  enum Direction {
    case Right, Down
  }

  case class GroupData(item:Any,items:Iterable[_])
}

class EachDirective(var `var`: String, var items: String, var area: Area, var direction: Direction = Direction.Down) extends AbstractDirective {
  private val GROUP_DATA_KEY: String = "_group"
  var select: String = null
  var groupBy: String = null
  var groupOrder: String = null
  var orderBy: String = null
  var multisheet: String = null
  var cellRefGenerator: CellRefGenerator = null

  addArea(area)

  override def applyAt(cellRef: CellRef, context: Context): Size = {
    var itemsCollection: Iterable[_] = null
    try {
      itemsCollection = transformToIterableObject(context.evaluator, items, context)
      itemsCollection = orderCollection(itemsCollection)
    } catch {
      case e: Exception =>
        logger.warn("Failed to evaluate collection expression {}", items, e)
        itemsCollection = List.empty
    }
    var size: Size = null
    if (groupBy == null || groupBy.length == 0) {
      size = processCollection(context, itemsCollection, cellRef, `var`)
    } else {
      val groupedData = group(itemsCollection, groupBy, groupOrder)
      val groupVar = if `var` != null then `var` else GROUP_DATA_KEY
      size = processCollection(context, groupedData, cellRef, groupVar)
    }
    if (direction == Direction.Down) area.transformer.adjustTableSize(cellRef, size)
    size
  }

  private def orderCollection(itemsCollection: Iterable[_]): Iterable[_] = {
    if (orderBy != null && !(orderBy.trim.isEmpty)) {
      val comp = if orderBy.contains(",") then new MultiPropertyOrdering(orderBy) else PropertyOrdering(orderBy)
      itemsCollection.toBuffer.sorted(comp)
    } else {
      itemsCollection
    }
  }

  private def group(cl: collection.Iterable[Any], groupProperty: String, groupOrder: String): Iterable[GroupData] = {
    if (cl == null) return Seq.empty
    val grouped = cl.groupBy(x => Properties.get[Any](x, groupProperty))
    grouped.map { g =>
      GroupData(g._1, g._2.toBuffer.sorted(new PropertyOrdering(groupOrder)))
    }
  }

  /**
   * Evaluates the passed collection name into an {@link Iterable} object
   *
   * @return an iterable object from the {@link Context} under given name
   */
  private def transformToIterableObject(evaluator: ExpressionEvaluator, collectionName: String, context: Context): Iterable[_] = {
    val collectionObject = evaluator.eval(collectionName, context.toMap)
    collectionObject match {
      case null => List.empty
      case a: Array[Any] => mutable.ArraySeq(a)
      case c: Collection[_] =>
        import scala.jdk.javaapi.CollectionConverters.asScala
        asScala(c)
      case i: Iterable[_] => i
      case _ => throw new RuntimeException(collectionName + " expression is not a collection or an array")
    }
  }

  private def processCollection(context: Context, itemsCollection: Iterable[_], cellRef: CellRef, varName: String): Size = {
    var newWidth: Int = 0
    var newHeight: Int = 0
    var cellRefGenerator: CellRefGenerator = this.cellRefGenerator
    if (cellRefGenerator == null && multisheet != null) {
      val sheetNameList = extractSheetNameList(context)
      cellRefGenerator = new MultiSheetCellRefGenerator(sheetNameList, cellRef)
    }
    var currentCell = cellRef
    val varIndex = varName + "_idx"
    val currentVarObject = context.getVar(varName)
    val currentVarIndexObject = context.getVar(varIndex)
    var currentIndex: Int = 0
    var breaked = false
    var index: Int = 0
    for (obj <- itemsCollection; if !breaked) {
      context.putVar(varName, obj)
      context.putVar(varIndex, currentIndex)
      if (select != null && !(context.isTrue(select))) {
        context.removeVar(varName)
      } else {
        if (cellRefGenerator != null) {
          index += 1
          currentCell = cellRefGenerator.generateCellRef(index - 1, context)
        }
        if (currentCell == null) {
          breaked = true
        } else {
          var size: Size = null
          try size = area.applyAt(currentCell, context)
          catch {
            case e: NegativeArraySizeException =>
              throw new RuntimeException("Check jx:each/lastCell parameter in template! Illegal area: " + area.getAreaRef, e)
          }
          if (cellRefGenerator != null) {
            newWidth = Math.max(newWidth, size.width)
            newHeight = Math.max(newHeight, size.height)
          } else {
            if (direction == Direction.Down) {
              currentCell = new CellRef(currentCell.sheetName, currentCell.row + size.height, currentCell.col)
              newWidth = Math.max(newWidth, size.width)
              newHeight += size.height
            } else { // RIGHT
              currentCell = new CellRef(currentCell.sheetName, currentCell.row, currentCell.col + size.width)
              newWidth += size.width
              newHeight = Math.max(newHeight, size.height)
            }
          }
          currentIndex += 1
        }
      }
    }
    restoreVarObject(context, varIndex, currentVarIndexObject)
    restoreVarObject(context, varName, currentVarObject)
    return new Size(newWidth, newHeight)
  }

  private def restoreVarObject(context: Context, varName: String, varObject: Any): Unit = {
    if (varName != null) {
      if varObject == null then context.removeVar(varName)
      else context.putVar(varName, varObject)
    }
  }

  private def extractSheetNameList(context: Context): collection.Seq[String] = {
    val sheetnames = context.getVar(multisheet).asInstanceOf[collection.Seq[String]]
    require(null != sheetnames && sheetnames.nonEmpty, s"cannot find nonempty ${multisheet}")
    sheetnames
  }
}
