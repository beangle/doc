package org.beangle.doc.core

class PrintOptions {
  /** 纸张大小 */
  var pageSize: PageSize = PageSize.A4
  /** 纸张方向 */
  var orientation: Orientation = Orientation.Portrait
  /** 是否打印背景 */
  var background: Boolean = false
  /** 页边距 */
  var margin: PageMargin = PageMargin.Zero
  /** 缩放到页面宽度 */
  var shrinkToFit: Boolean = true
  /** 缩放比 */
  var scale: Double = 1.0
  /** 是否打印页眉页脚 */
  var footheader: Boolean = _
  /** 打印拷贝数 */
  var copys: Int = 1
}

/** 打印方向
 * Portrait 为纵向
 * Landscape 为横向
 */
enum Orientation(val id: Int, val name: String) {
  case Portrait extends Orientation(1, "Portrait")
  case Landscape extends Orientation(2, "Landscape")
}

object Orientation {
  def fromId(id: Int): Orientation = fromOrdinal(id - 1)
}

/** 纸张大小
 * 定义常规的纸张大小
 */
enum PageSize(val name: String) {

  /** 594 x 841 mm */
  case A1 extends PageSize("A1")
  /** 420 x 594 mm */
  case A2 extends PageSize("A2")
  /** 297 x 420 mm */
  case A3 extends PageSize("A3")
  /** 210 x 297 mm, 8.26 x 11.69 inches */
  case A4 extends PageSize("A4")
  /** 148 x 210 mm */
  case A5 extends PageSize("A5")
  /** 105 x 148 mm */
  case A6 extends PageSize("A6")
  /** 8.5 x 11 inches, 215.9 x 279.4 mm */
  case Letter extends PageSize("Letter")
}

object PageMargin {
  val Default = PageMargin(1.0, 1.0, 1.0, 1.0)
  val Zero = PageMargin(0, 0, 0, 0)
}

case class PageMargin(top: Double, bottom: Double, left: Double, right: Double) {

}
