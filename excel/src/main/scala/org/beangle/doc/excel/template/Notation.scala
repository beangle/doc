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

import java.util.regex.Pattern

object Notation {

  val ExpressionBegin = "${"
  val ExpressionEnd = "}"
  val ExpressionPattern = Pattern.compile("\\$\\{[^}]*}")

  val USER_FORMULA_PREFIX: String = "$["
  val USER_FORMULA_SUFFIX: String = "]"

  val DirectivePrefox = "jx:"
  val ATTR_PREFIX: String = "("
  val ATTR_SUFFIX: String = ")"
  val JX_PARAMS_PREFIX: String = "jx:params"
  val ATTR_REGEX = "\\s*\\w+\\s*=\\s*([\"|'\u201C\u201D\u201E\u201F\u2033\u2036\u2018\u2019\u201A\u201B\u2032\u2035])(?:(?!\\1).)*\\1"
  val ATTR_REGEX_PATTERN = Pattern.compile(ATTR_REGEX)
  val AREAS_ATTR_REGEX = "areas\\s*=\\s*\\[[^]]*]"
  val AREAS_ATTR_REGEX_PATTERN = Pattern.compile(AREAS_ATTR_REGEX)

  val LAST_CELL_ATTR_NAME = "lastCell"

  def isDirectiveString(str: String): Boolean = str.startsWith(DirectivePrefox) && !str.startsWith(JX_PARAMS_PREFIX)

  def isJxComment(cellComment: String): Boolean = {
    if (cellComment == null) return false
    val commentLines = cellComment.split("\\n")
    commentLines.exists { commentLine =>
      (commentLine != null) && Notation.isDirectiveString(commentLine.trim)
    }
  }
}
