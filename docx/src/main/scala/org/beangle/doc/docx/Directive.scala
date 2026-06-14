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

/** 从模板文本解析出的 `[#kind … /]` 指令。 */
final case class Directive(
  kind: String,
  placeholder: String,
  /** 无属性时取指令体裸值；有 `name=` 属性时取其值（已去引号）。 */
  name: Option[String],
  properties: Map[String, String]
)

object Directive {

  def byPlaceholder(directives: Seq[Directive]): Map[String, Directive] =
    directives.map(d => d.placeholder -> d).toMap
}
