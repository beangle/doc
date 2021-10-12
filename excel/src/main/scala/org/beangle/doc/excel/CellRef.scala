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

package org.beangle.doc.excel

import org.apache.poi.ss.util.{AreaReference, CellReference}
import org.beangle.commons.lang.Strings
import org.beangle.doc.excel.CellRef
import org.beangle.doc.excel.CellRef.*

import java.util.regex.Pattern

object CellRef {
  val NONE = new CellRef("NONE", -1, -1)
  private val DELIMITER = '\''
  /** Matches a single cell ref with no absolute ('$') markers */
  private val CELL_REF_PATTERN = Pattern.compile("([A-Za-z]+)([0-9]+)")
  /** The character (!) that separates sheet names from cell references */
  val SHEET_NAME_DELIMITER = '!'
  /** The character (:) that separates the two cell references in a multi-cell area reference */
  private val CELL_DELIMITER = ':'
  /** The character ($) that signifies a row or column value is absolute instead of relative */
  val ABSOLUTE_REFERENCE_MARKER = '$'

  def apply(cellRef: String): CellRef = {
    apply(new CellReference(cellRef))
  }

  def apply(cr: CellReference): CellRef = {
    val c = new CellRef(cr.getSheetName, cr.getRow, cr.getCol)
    c.isColAbs = cr.isColAbsolute
    c.isRowAbs = cr.isRowAbsolute
    c
  }

  def apply(row: Int, col: Int): CellRef = {
    new CellRef("", row, col)
  }

  def isPlainColumn(refPart: String): Boolean = {
    !(refPart.length - 1 to 0 by -1).exists { i =>
      val ch = refPart.charAt(i)
      (!(ch == '$' && i == 0)) && (ch < 'A' || ch > 'Z')
    }
  }

  /**
   * Cell comparator used to order cells first by columns and then by rows
   */
  object ColPrecedence extends Ordering[CellRef] {
    override def compare(cellRef1: CellRef, cellRef2: CellRef): Int = {
      if (cellRef1 eq cellRef2) return 0
      if (cellRef1 == null) return 1
      if (cellRef2 == null) return -1
      if (cellRef1.sheetName != null && cellRef2.sheetName != null) {
        val sheetNameCompared = cellRef1.sheetName.compareTo(cellRef2.sheetName)
        if (sheetNameCompared != 0) return sheetNameCompared
      }
      else if (cellRef1.sheetName != null || cellRef2.sheetName != null) return if (cellRef1.sheetName != null) -1
      else 1
      if (cellRef1.col < cellRef2.col) return -1
      if (cellRef1.col > cellRef2.col) return 1
      if (cellRef1.row < cellRef2.row) return -1
      if (cellRef1.row > cellRef2.row) return 1
      0
    }
  }

  /**
   * Cell comparator used to order cell by rows first and then by columns
   */
  object RowPrecedence extends Ordering[CellRef] {
    override def compare(cellRef1: CellRef, cellRef2: CellRef): Int = {
      if (cellRef1 eq cellRef2) return 0
      if (cellRef1 == null) return 1
      if (cellRef2 == null) return -1
      if (cellRef1.sheetName != null && cellRef2.sheetName != null) {
        val sheetNameCompared = cellRef1.sheetName.compareTo(cellRef2.sheetName)
        if (sheetNameCompared != 0) return sheetNameCompared
      }
      else if (cellRef1.sheetName != null || cellRef2.sheetName != null) return if (cellRef1.sheetName != null) -1
      else 1
      if (cellRef1.row < cellRef2.row) return -1
      if (cellRef1.row > cellRef2.row) return 1
      if (cellRef1.col < cellRef2.col) return -1
      if (cellRef1.col > cellRef2.col) return 1
      0
    }
  }
}

class CellRef(var sheetName: String, var row: Int, var col: Int) extends Comparable[CellRef] {
  var isColAbs = false
  var isRowAbs = false

  def getCellName(ignoreSheetName: Boolean = false): String = {
    val sb = new java.lang.StringBuilder(32)
    if (!ignoreSheetName && Strings.isNotBlank(sheetName)) {
      sb.append(sheetName)
      sb.append(SHEET_NAME_DELIMITER)
    }
    if (isColAbs) sb.append(ABSOLUTE_REFERENCE_MARKER)
    sb.append(CellReference.convertNumToColString(col))
    if (isRowAbs) sb.append(ABSOLUTE_REFERENCE_MARKER)
    sb.append(row + 1)
    sb.toString()
  }

  def move(rowplus: Int, colPlus: Int): CellRef = {
    new CellRef(sheetName, rowplus + row, colPlus + col)
  }

  def getFormattedSheetName = sheetName

  override def equals(o: Any): Boolean = {
    o match {
      case null => false
      case cellRef: CellRef => !(col != cellRef.col || row != cellRef.row || !(sheetName == cellRef.sheetName))
      case _ => false
    }
  }

  override def hashCode: Int = {
    var result = col
    result = 31 * result + row
    result = 31 * result + sheetName.hashCode
    result
  }

  def isValid = col >= 0 && row >= 0

  override def toString: String = getCellName(false)

  override def compareTo(that: CellRef): Int = {
    if (this eq that) return 0
    if (col < that.col) return -1
    if (col > that.col) return 1
    if (row < that.row) return -1
    if (row > that.row) return 1
    0
  }
}
