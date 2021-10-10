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

import org.apache.poi.ss.formula.FormulaParseException
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellReference
import org.apache.poi.xssf.usermodel.XSSFCell
import org.beangle.commons.io.DataType
import org.beangle.commons.lang.{Objects, Strings}
import org.beangle.doc.excel.CellOps.*
import org.beangle.doc.excel.template.CellData.*
import org.beangle.doc.excel.template.Notation.*
import org.beangle.doc.excel.{AreaRef, CellRef, Sheets}
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTCell
import org.slf4j.{Logger, LoggerFactory}

import java.sql.Timestamp
import java.time.*
import java.util
import java.util.regex.{Matcher, Pattern}
import java.util.{Date, Map}
import scala.collection.mutable

object CellData {

  /*
   * In addition to normal (straight) single and double quotes, this regex
   * includes the following commonly occurring quote-like characters (some
   * of which have been observed in recent versions of LibreOffice):
   *
   * U+201C - LEFT DOUBLE QUOTATION MARK
   * U+201D - RIGHT DOUBLE QUOTATION MARK
   * U+201E - DOUBLE LOW-9 QUOTATION MARK
   * U+201F - DOUBLE HIGH-REVERSED-9 QUOTATION MARK
   * U+2033 - DOUBLE PRIME
   * U+2036 - REVERSED DOUBLE PRIME
   * U+2018 - LEFT SINGLE QUOTATION MARK
   * U+2019 - RIGHT SINGLE QUOTATION MARK
   * U+201A - SINGLE LOW-9 QUOTATION MARK
   * U+201B - SINGLE HIGH-REVERSED-9 QUOTATION MARK
   * U+2032 - PRIME
   * U+2035 - REVERSED PRIME
   */
  private val ATTR_REGEX: String = "\\s*\\w+\\s*=\\s*([\"|'\u201C\u201D\u201E\u201F\u2033\u2036\u2018\u2019\u201A\u201B\u2032\u2035])(?:(?!\\1).)*\\1"
  private val ATTR_REGEX_PATTERN = Pattern.compile(ATTR_REGEX)
  private val FORMULA_STRATEGY_PARAM: String = "formulaStrategy"
  private val DEFAULT_VALUE: String = "defaultValue"
  private val logger = LoggerFactory.getLogger(classOf[CellData])

  enum FormulaStrategy {
    case DEFAULT, BY_COLUMN, BY_ROW
  }

  private def isUserFormula(str: String): Boolean = str.startsWith(USER_FORMULA_PREFIX) && str.endsWith(USER_FORMULA_SUFFIX)

  def createCellData(rowData: RowData, cellRef: CellRef, cell: Cell): CellData = {
    val cellData = new CellData(cellRef, cell)
    cellData.rowData = rowData
    cellData.readCell(cell)
    cellData.updateFormulaValue()
    cellData
  }

  def parseDirectiveAttributes(attrString: String): mutable.Map[String, String] = {
    val attrMap = new mutable.LinkedHashMap[String, String]
    val attrMatcher = ATTR_REGEX_PATTERN.matcher(attrString)
    while (attrMatcher.find) {
      val attrData = attrMatcher.group
      val attrNameEndIndex = attrData.indexOf("=")
      val attrName = attrData.substring(0, attrNameEndIndex).trim
      val attrValuePart = attrData.substring(attrNameEndIndex + 1).trim
      val attrValue = attrValuePart.substring(1, attrValuePart.length - 1)
      attrMap.put(attrName, attrValue)
    }
    attrMap
  }

}

class CellData(val cellRef: CellRef, var cell: Cell) {
  var attrMap: mutable.Map[String, String] = _
  var cellValue: Any = _
  var cellType: DataType = _
  var cellComment: String = _
  var formula: String = _
  var evaluationResult: Any = _
  protected var targetCellType: DataType = _
  var formulaStrategy: CellData.FormulaStrategy = CellData.FormulaStrategy.DEFAULT
  var defaultValue: String = _
  var area: Area = _

  private var rowData: RowData = _
  private var richTextString: RichTextString = _
  var cellStyle: CellStyle = _
  private var hyperlink: Hyperlink = _
  private var comment: Comment = _
  private var commentAuthor: String = _

  val targetPos = new mutable.ArrayBuffer[CellRef]
  val targetParentAreaRef = new mutable.ArrayBuffer[AreaRef]
  var evaluatedFormulas = new mutable.ArrayBuffer[String]

  def this(sheetName: String, row: Int, col: Int, cellType: DataType, cellValue: Any) = {
    this(new CellRef(sheetName, row, col), null)
    this.cellType = cellType
    this.cellValue = cellValue
    updateFormulaValue()
  }

  def this(cellRef: CellRef, cellType: DataType, cellValue: Any) = {
    this(cellRef, null)
    this.cellType = cellType
    this.cellValue = cellValue
    updateFormulaValue()
  }

  def this(sheetName: String, row: Int, col: Int) = {
    this(sheetName, row, col, DataType.Blank, null)
  }

  def sheetName: String = cellRef.sheetName

  def row: Int = cellRef.row

  def col: Int = cellRef.col

  def isFormulaCell: Boolean = formula != null

  def isParameterizedFormulaCell: Boolean = isFormulaCell && CellData.isUserFormula(cellValue.toString)

  def isJointedFormulaCell: Boolean = isParameterizedFormulaCell && FormulaProcessor.formulaContainsJointedCellRef(cellValue.toString)

  def addTargetPos(cellRef: CellRef): Unit = targetPos.addOne(cellRef)

  def addTargetParentAreaRef(areaRef: AreaRef): Unit = {
    targetParentAreaRef.addOne(areaRef)
  }

  def resetTargetPos(): Unit = {
    targetPos.clear()
    targetParentAreaRef.clear()
  }

  def evaluate(context: Context): Any = {
    targetCellType = cellType
    if ((cellType == DataType.String) && cellValue != null) {
      val strValue = cellValue.toString
      if (CellData.isUserFormula(strValue)) {
        val formulaStr = strValue.substring(USER_FORMULA_PREFIX.length, strValue.length - USER_FORMULA_SUFFIX.length)
        evaluate(formulaStr, context)
        if (evaluationResult != null) {
          targetCellType = DataType.Formula
          formula = evaluationResult.toString
          evaluatedFormulas.addOne(formula)
        }
      }
      else evaluate(strValue, context)
      if (evaluationResult == null) targetCellType = DataType.Blank
    }
    evaluationResult
  }

  private def evaluate(strValue: String, context: Context): Unit = {
    val sb = new java.lang.StringBuilder
    val beginExpressionLength = Notation.ExpressionBegin.length
    val endExpressionLength = Notation.ExpressionEnd.length
    val exprMatcher: Matcher = Notation.ExpressionPattern.matcher(strValue)
    val evaluator = context.evaluator
    var matchedString: String = null
    var expression: String = null
    var lastMatchEvalResult: Any = null
    var matchCount: Int = 0
    var endOffset: Int = 0
    while (exprMatcher.find) {
      endOffset = exprMatcher.end
      matchCount += 1
      matchedString = exprMatcher.group
      expression = matchedString.substring(beginExpressionLength, matchedString.length - endExpressionLength)
      lastMatchEvalResult = evaluator.eval(expression, context.toMap)
      exprMatcher.appendReplacement(sb, Matcher.quoteReplacement(if (lastMatchEvalResult != null) lastMatchEvalResult.toString else ""))
    }
    val lastStringResult = if (lastMatchEvalResult != null) lastMatchEvalResult.toString else ""
    val isAppendTail = matchCount == 1 && endOffset < strValue.length
    if (matchCount > 1 || isAppendTail) {
      exprMatcher.appendTail(sb)
      evaluationResult = sb.toString
    } else if (matchCount == 1) {
      if (sb.length > lastStringResult.length) evaluationResult = sb.toString
      else {
        evaluationResult = lastMatchEvalResult
        setTargetCellType()
      }
    } else if (matchCount == 0) evaluationResult = strValue
  }

  protected def updateFormulaValue(): Unit = {
    if (cellType == DataType.Formula) formula = if (cellValue != null) cellValue.toString
    else ""
    else if ((cellType == DataType.String) && cellValue != null && CellData.isUserFormula(cellValue.toString)) {
      formula = cellValue.toString.substring(2, cellValue.toString.length - 1)
    }
  }

  protected def isParamsComment(cellComment: String): Boolean = cellComment.trim.startsWith(JX_PARAMS_PREFIX)

  private def setTargetCellType(): Unit = {
    targetCellType = DataType.toType(evaluationResult.getClass)
  }

  /**
   * The method parses jx:params attribute from a cell comment
   * <p>jx:params can be used e.g.</p><ul>
   * <li>to set  via 'formulaStrategy' param</li>
   * <li>to set the formula default value via 'defaultValue' param</li></ul>
   *
   * @param cellComment the comment string
   */
  protected def processParams(cellComment: String): Unit = {
    val nameEndIndex = cellComment.indexOf(ATTR_PREFIX, JX_PARAMS_PREFIX.length)
    if (nameEndIndex < 0) {
      val errMsg = "Failed to parse params [" + cellComment + "] at " + cellRef.getCellName + ". Expected '" + ATTR_PREFIX + "' symbol."
      logger.error(errMsg)
      throw new IllegalStateException(errMsg)
    }
    attrMap = buildAttrMap(cellComment, nameEndIndex)
    if (attrMap.contains(CellData.FORMULA_STRATEGY_PARAM)) initFormulaStrategy(attrMap(CellData.FORMULA_STRATEGY_PARAM))
    if (attrMap.contains(CellData.DEFAULT_VALUE)) defaultValue = attrMap(CellData.DEFAULT_VALUE)
  }

  private def buildAttrMap(paramsLine: String, nameEndIndex: Int): mutable.Map[String, String] = {
    val paramsEndIndex = paramsLine.lastIndexOf(ATTR_SUFFIX)
    if (paramsEndIndex < 0) {
      val errMsg: String = "Failed to parse params line [" + paramsLine + "] at " + cellRef.getCellName + ". Expected '" + ATTR_SUFFIX + "' symbol."
      logger.error(errMsg)
      throw new IllegalArgumentException(errMsg)
    }
    val attrString = paramsLine.substring(nameEndIndex + 1, paramsEndIndex).trim
    parseDirectiveAttributes(attrString)
  }

  private def initFormulaStrategy(formulaStrategyValue: String): Unit = {
    try this.formulaStrategy = CellData.FormulaStrategy.valueOf(formulaStrategyValue)
    catch {
      case e: IllegalArgumentException =>
        throw new RuntimeException("Cannot parse formula strategy value at " + cellRef.getCellName, e)
    }
  }

  def readCell(cell: Cell): Unit = {
    readCellGeneralInfo(cell)
    readCellContents(cell)
    readCellStyle(cell)
  }

  private def readCellGeneralInfo(cell: Cell): Unit = {
    hyperlink = cell.getHyperlink
    comment = cell.getCellComment
    if (comment != null) commentAuthor = comment.getAuthor
    if (comment != null && comment.getString != null && comment.getString.getString != null) {
      val commentString: String = comment.getString.getString
      val commentLines: Array[String] = commentString.split("\\n")
      for (commentLine <- commentLines) {
        if (isParamsComment(commentLine)) {
          processParams(commentLine)
          comment = null
          return
        }
      }
      cellComment = commentString
    }
  }

  private def readCellContents(cell: Cell): Unit = {
    cell.getCellType match {
      case CellType.STRING =>
        richTextString = cell.getRichStringCellValue
        cellValue = richTextString.getString
        cellType = DataType.String
      case CellType.BOOLEAN =>
        cellValue = cell.getBooleanCellValue
        cellType = DataType.Boolean
      case CellType.NUMERIC =>
        readNumericCellContents(cell)
      case CellType.FORMULA =>
        formula = cell.getCellFormula
        cellValue = formula
        cellType = DataType.Formula
      case CellType.ERROR =>
        cellValue = cell.getErrorCellValue
        cellType = DataType.Error
      case CellType.BLANK =>
      case CellType._NONE =>
        cellValue = null
        cellType = DataType.Blank
    }
    evaluationResult = cellValue
  }

  private def readNumericCellContents(cell: Cell): Unit = {
    if (DateUtil.isCellDateFormatted(cell)) {
      cellValue = cell.getDateCellValue
      cellType = DataType.Date
    } else {
      cellValue = cell.getNumericCellValue
      cellType = DataType.Double
    }
  }

  private def readCellStyle(cell: Cell): Unit = {
    cellStyle = cell.getCellStyle
  }

  def writeToCell(cell: Cell, context: Context, transformer: DefaultTransformer): Unit = {
    evaluate(context)
    updateCellGeneralInfo(cell)
    updateCellContents(cell)
    updateCellStyle(cell, cellStyle)
    rowData.sheetData.updateConditionalFormatting(this, cell)
  }

  private def updateCellGeneralInfo(cell: Cell): Unit = {
    if (targetCellType != DataType.Formula) cell.setCellType(getPoiCellType(targetCellType))
    if (hyperlink != null) cell.setHyperlink(hyperlink)
    if (comment != null && !Notation.isJxComment(cellComment)) cell.setComment(cellComment, commentAuthor, null)
  }

  private def getPoiCellType(cellType: DataType): CellType = {
    if (cellType == null) return org.apache.poi.ss.usermodel.CellType.BLANK
    cellType match {
      case DataType.String => CellType.STRING
      case DataType.Boolean => CellType.BOOLEAN
      case DataType.Integer | DataType.Float | DataType.Double | DataType.Date | DataType.Time | DataType.DateTime => CellType.NUMERIC
      case DataType.Instant | DataType.YearMonth | DataType.MonthDay => CellType.NUMERIC
      case DataType.Formula => CellType.FORMULA
      case DataType.Error => CellType.ERROR
      case DataType.Blank => CellType.BLANK
      case _ => CellType.BLANK
    }
  }

  private def updateCellContents(cell: Cell): Unit = {
    if (evaluationResult == null) {
      cell.setBlank()
    } else {
      targetCellType match {
        case DataType.String => updateStringCellContents(cell)
        case DataType.Boolean => cell.setCellValue(evaluationResult.asInstanceOf[Boolean])
        case DataType.Date =>
          evaluationResult match {
            case s: java.sql.Date => cell.setCellValue(s)
            case ld: LocalDate => cell.setCellValue(ld)
          }
        case DataType.Time => cell.setCellValue(evaluationResult.asInstanceOf[LocalTime].atDate(LocalDate.now))
        case DataType.DateTime =>
          evaluationResult match {
            case ts: Timestamp => cell.setCellValue(ts)
            case ldt: LocalDateTime => cell.setCellValue(ldt)
            case jud: java.util.Date => cell.setCellValue(jud)
          }
        case DataType.ZonedDateTime =>
          cell.setCellValue(evaluationResult.asInstanceOf[ZonedDateTime].toLocalDateTime)
        case DataType.Instant =>
          cell.setCellValue(evaluationResult.asInstanceOf[Instant].atZone(ZoneId.systemDefault).toLocalDateTime)
        case DataType.Integer | DataType.Float | DataType.Double => cell.setCellValue(evaluationResult.asInstanceOf[Number].doubleValue)
        case DataType.Formula => updateFormulaCellContents(cell)
        case DataType.Error => cell.setCellErrorValue(evaluationResult.asInstanceOf[Byte])
        case DataType.Blank => cell.setBlank()
        case _ => cell.setCellValue(evaluationResult.toString)
      }
    }
  }

  private def updateStringCellContents(cell: Cell): Unit = {
    if (evaluationResult.isInstanceOf[Array[Byte]]) return
    val result: String = if (evaluationResult != null) evaluationResult.toString
    else ""
    if (cellValue != null && cellValue == result) cell.setCellValue(richTextString)
    else cell.setCellValue(result)
  }

  private def updateFormulaCellContents(cell: Cell): Unit = {
    val evaluateResultStr = evaluationResult.toString
    try {
      if (FormulaProcessor.formulaContainsJointedCellRef(evaluateResultStr)) cell.setCellValue(evaluateResultStr)
      else {
        cell.setCellFormula(evaluateResultStr)
        cell.clearValue()
      }
    } catch {
      case e: FormulaParseException =>
        try {
          logger.error("Failed to set cell formula " + evaluateResultStr + " for cell " + this.toString, e)
          cell.setCellType(CellType.STRING)
          cell.setCellValue(evaluateResultStr)
        } catch {
          case _: Exception => logger.warn("Failed to convert formula to string for cell " + this.toString)
        }
    }
  }

  private def updateCellStyle(cell: Cell, cellStyle: CellStyle): Unit = {
    cell.setCellStyle(cellStyle)
  }

  override def toString: String = "CellData{" + cellRef + ", cellType=" + cellType + ", cellValue=" + cellValue + '}'

  override def equals(o: Any): Boolean = {
    o match {
      case null => false
      case cd: CellData =>
        if this eq cd then true
        else Objects.equals(cellType, cd.cellType) && Objects.equals(cellValue, cd.cellValue) && Objects.equals(cellRef, cd.cellRef)
      case _ => false
    }
  }

  override def hashCode: Int = {
    var result = if (cellRef != null) cellRef.hashCode else 0
    result = 31 * result + (if (cellValue != null) cellValue.hashCode else 0)
    result = 31 * result + (if (cellType != null) cellType.hashCode else 0)
    result
  }
}
