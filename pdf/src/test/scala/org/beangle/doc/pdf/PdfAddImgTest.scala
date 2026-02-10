package org.beangle.doc.pdf

import com.itextpdf.kernel.pdf.{PdfDocument, PdfReader}
import org.beangle.commons.io.Dirs
import org.beangle.commons.io.Files./

import java.io.{File, FileInputStream}

object PdfAddImgTest {

  def main(args: Array[String]): Unit = {
    val srcDir = "D:\\tmp\\2016"
    val image = new File("D:\\tmp\\signature_old.png").toURI.toURL
    val targetDir = srcDir + / + "signatures"
    new File(srcDir + / + "signatures").mkdirs()
    Dirs.on(srcDir).ls() foreach { n =>
      val file = new File(srcDir + / + n)
      val target = new File(targetDir + / + n)
      if (file.isFile && n.endsWith(".pdf")) {
        val doc = new PdfDocument(new PdfReader(new FileInputStream(file)))
        val locs = Texts.getLocations(doc, "上海音乐学院研究生部培")
        doc.close()
        if (locs.nonEmpty) {
          val loc = locs.head
          val stampX = loc._1 - 30 //偏左一点，以免盖住成绩
          val stampY = loc._2 - (55f - 45f) //减去图章半径，稍微上一点
          Docs.addImage(file, target, image, (stampX, stampY), (110f, 110f))
        }
      }
    }
  }
}
