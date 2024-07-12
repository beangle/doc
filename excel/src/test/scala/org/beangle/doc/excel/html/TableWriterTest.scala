package org.beangle.doc.excel.html

import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.beangle.commons.io.Files
import org.beangle.doc.excel.html.TableParser.parse

import java.io.{File, FileOutputStream}

object TableWriterTest {

  def main(args: Array[String]): Unit = {
    val file = File.createTempFile("template", ".xlsx")
    val workbook = new XSSFWorkbook()
    val sheet = workbook.createSheet()
    val t = Files.readString(new File("D:\\workspace\\beangle\\doc\\excel\\src\\test\\resources\\table.html"))
    val table = TableParser.parse(t)
    TableWriter.write(table, sheet, 1)
    val os = new FileOutputStream(file)
    //打印相关
    sheet.setRepeatingRows(CellRangeAddress.valueOf("2:3"))
    sheet.setZoom(80)

    //sheet.setFitToPage(true)
    val ps = sheet.getPrintSetup
    ps.setScale(57)
    ps.setLandscape(false)
    workbook.write(os)
    println(file.getAbsolutePath)
    os.close()
  }

  def main1(args: Array[String]): Unit = {
    val t = Files.readString(new File("D:\\workspace\\beangle\\doc\\excel\\src\\test\\resources\\table.html"))
    val table = parse(t)
    println(table.toXml)
  }

}
