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

package org.beangle.doc.docx

import org.apache.poi.xwpf.usermodel.{XWPFNumbering, XWPFParagraph}
import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.Strings
import org.beangle.commons.text.seq.HanZiSeqStyle

/** 读取word中的自动编号
 *
 * @param numbering
 */
class NumberingReader(numbering: XWPFNumbering) {
  private val counters = Collections.newMap[String, Int]

  def extract(xp: XWPFParagraph): String = {
    if (numbering != null && xp.getCTPPr.isSetNumPr) {
      val numPr = xp.getCTPPr.getNumPr
      if (null == numPr || null == numPr.getIlvl) return ""
      val numId = xp.getNumID
      val ilvl = numPr.getIlvl.getVal
      val num = numbering.getNum(numId)
      if (num != null) {
        // 获取抽象编号ID
        val abstractNumId = num.getCTNum.getAbstractNumId.getVal
        // 创建唯一的计数器键
        val counterKey = s"${numId}-${ilvl}"
        // 获取或初始化计数器
        val curr = counters.getOrElseUpdate(counterKey, 0) + 1
        counters.put(counterKey, curr)
        // 获取编号格式信息
        val abstractNum = numbering.getAbstractNum(abstractNumId).getCTAbstractNum
        val level = abstractNum.getLvlArray(ilvl.intValue)
        if (level != null && level.getNumFmt != null) {
          val lvlText = if level.getLvlText != null then level.getLvlText.getVal else ""
          // 使用实际的计数器值格式化编号
          return formatNumbering(level.getNumFmt.getVal.toString, lvlText, curr)
        }
      }
    }
    ""
  }

  private def formatNumbering(format: String, lvlText: String, counter: Int) = {
    val number =
      format.toLowerCase match {
        case "decimal" => String.valueOf(counter)
        case "upperletter" => toLetters(counter, true)
        case "lowerletter" => toLetters(counter, false)
        case "upperroman" => toRoman(counter).toUpperCase
        case "lowerroman" => toRoman(counter).toLowerCase
        case "chinesecounting" => toHanzi(counter)
        case "japanesecounting" => toHanzi(counter) //Kanji same as Hanzi
        case _ => String.valueOf(counter)
      }
    //@see https://www.w3school.com.cn/charsets/ref_utf_geometric.asp
    var t = lvlText
    t = Strings.replace(t, "\uF06C", "●") //替换小圆点
    t = Strings.replace(t, "\uF0A7", "■") //替换小圆点
    // 处理级别文本模板
    if (t.isEmpty) number + "."
    else t.replace("%1", number) + " " // 替换级别文本中的占位符
  }

  private def toHanzi(i: Int): String = {
    new HanZiSeqStyle().buildText(i.toString)
  }

  private def toLetters(num: Int, upperCase: Boolean) = {
    val result = new StringBuilder
    var number = num
    while (number > 0) {
      number -= 1 // 转换为0基础索引
      val remainder = number % 26
      val letter = ((if (upperCase) 'A' else 'a') + remainder).toChar
      result.insert(0, letter)
      number = number / 26
    }
    result.toString
  }

  private def toRoman(num: Int) = {
    var number = num
    val romanSymbols = Array("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
    val values = Array(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    val roman = new StringBuilder
    var i = 0
    while (number > 0) {
      while (number >= values(i)) {
        number -= values(i)
        roman.append(romanSymbols(i))
      }
      i += 1
    }
    roman.toString
  }
}
