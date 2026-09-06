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

package org.beangle.doc.excel.stream

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.io.{File, FileOutputStream}
import java.nio.file.Files
import java.time.{LocalDate, LocalDateTime, ZoneId}
import java.util.Random
import scala.util.Using

/** StreamingWriter / StreamingReader 效率与内存对比测试。
 *
 * 混合类型测试数据（字符串、日期、数字）：
 *  1. 效率测试：默认 50000*20，流式写盘 -> 流式读回，输出耗时、吞吐、文件大小
 *  2. 内存对比：默认 10000*20（受传统 XSSFWorkbook 全量载入内存限制），
 *     分别用 StreamingWriter/XSSFWorkbook 写、StreamingReader/XSSFWorkbook 读，
 *     对比各阶段增量堆内存与耗时
 *
 * 可用环境变量调整规模或保存结果文件（系统属性 excel.perf.* 优先级更高）：
 *  EXCEL_PERF_ROWS=10000 EXCEL_PERF_COLS=20 EXCEL_PERF_FILE=/tmp/perf.xlsx
 *  EXCEL_PERF_MEM_ROWS=20000 EXCEL_PERF_MEM_COLS=20
 */
class StreamingPerformanceTest extends AnyFunSuite with Matchers {

  private def param(key: String, env: String, default: Int): Int = {
    sys.props.get(key).orElse(sys.env.get(env)).map(_.toInt).getOrElse(default)
  }

  private val rows: Int = param("excel.perf.rows", "EXCEL_PERF_ROWS", 50000)
  private val cols: Int = param("excel.perf.cols", "EXCEL_PERF_COLS", 20)
  private val cellCount: Long = rows.toLong * cols

  private val memRows: Int = param("excel.perf.memRows", "EXCEL_PERF_MEM_ROWS", 10000)
  private val memCols: Int = param("excel.perf.memCols", "EXCEL_PERF_MEM_COLS", 20)

  private val file: File = {
    sys.props.get("excel.perf.file").orElse(sys.env.get("EXCEL_PERF_FILE")) match {
      case Some(path) =>
        val f = new File(path)
        Files.deleteIfExists(f.toPath)
        f
      case None => tempFile("beangle-streaming-perf")
    }
  }

  private val depts = Array("研发部", "市场部", "销售部", "人事部", "财务部", "运维部", "法务部", "客服部")
  private val titles = Array("工程师", "经理", "总监", "专员", "主管", "助理", "顾问", "分析师")

  private def fillRow(i: Int, row: Array[Any], rand: Random): Unit = {
    row(0) = i + 1
    row(1) = f"用户${i + 1}%06d"
    row(2) = if ((i & 1) == 0) "男" else "女"
    row(3) = LocalDate.of(1970, 1, 1).plusDays(rand.nextInt(20000))
    row(4) = rand.nextDouble() * 100
    row(5) = 150 + rand.nextDouble() * 50
    row(6) = 40 + rand.nextDouble() * 80
    row(7) = 3000 + rand.nextDouble() * 70000
    row(8) = s"user${i + 1}@beangle.org"
    row(9) = "138" + (0 until 8).map(_ => rand.nextInt(10)).mkString
    row(10) = depts(rand.nextInt(depts.length))
    row(11) = titles(rand.nextInt(titles.length))
    row(12) = LocalDate.of(2015, 1, 1).plusDays(rand.nextInt(4000))
    row(13) = LocalDateTime.of(2020, 1, 1, 0, 0).plusSeconds(rand.nextInt(31536000))
    row(14) = rand.nextInt(5) + 1
    row(15) = rand.nextBoolean()
    row(16) = s"备注${rand.nextInt(10000)}"
    row(17) = rand.nextDouble()
    row(18) = rand.nextDouble() * 1000000
    row(19) = LocalDate.of(2020, 1, 1).plusDays(rand.nextInt(2000))
    var j = 20
    while (j < row.length) {
      row(j) = s"填充列$j-${rand.nextInt(1000)}"
      j += 1
    }
  }

  private def headers(colCount: Int): Seq[String] = (0 until colCount).map(i => s"列${i + 1}")

  private def tempFile(prefix: String): File = {
    val f = Files.createTempFile(prefix, ".xlsx").toFile
    f.deleteOnExit()
    f
  }

  /** 流式写入：生成一行写一行，不整表驻留内存；save 后 workbook 已关闭。 */
  private def writeStreaming(target: File, rowCount: Int, colCount: Int,
                             onLive: () => Unit = () => ()): Unit = {
    val writer = new StreamingWriter()
    try {
      writer.writeHeaders(headers(colCount)*)
      val row = new Array[Any](colCount)
      val rand = new Random(42)
      var i = 0
      while (i < rowCount) {
        fillRow(i, row, rand)
        writer.writeRow(row*)
        i += 1
      }
      onLive()
      writer.save(target)
    } catch {
      case t: Throwable =>
        writer.close()
        throw t
    }
  }

  /** 传统 POI 全内存写入：整张表在内存中构建后再落盘。 */
  private def writeXssf(target: File, rowCount: Int, colCount: Int,
                        onLive: () => Unit = () => ()): Unit = {
    val workbook = new XSSFWorkbook()
    try {
      val sheet = workbook.createSheet("sample")
      val row = new Array[Any](colCount)
      val rand = new Random(42)
      var i = 0
      while (i < rowCount) {
        fillRow(i, row, rand)
        val xssfRow = sheet.createRow(i)
        var c = 0
        while (c < colCount) {
          val cell = xssfRow.createCell(c)
          row(c) match {
            case s: String            => cell.setCellValue(s)
            case d: java.lang.Double  => cell.setCellValue(d.doubleValue())
            case n: java.lang.Integer => cell.setCellValue(n.intValue())
            case b: java.lang.Boolean => cell.setCellValue(b.booleanValue())
            case ld: LocalDate        => cell.setCellValue(java.sql.Date.valueOf(ld))
            case ldt: LocalDateTime =>
              cell.setCellValue(java.util.Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant))
            case v => cell.setCellValue(v.toString)
          }
          c += 1
        }
        i += 1
      }
      onLive()
      val fos = new FileOutputStream(target)
      try workbook.write(fos)
      finally fos.close()
    } finally workbook.close()
  }

  /** 流式读取：跳过表头，返回(数据行数, 单元格数)。 */
  private def readStreaming(source: File, onLive: () => Unit = () => ()): (Long, Long) = {
    var dataRows = 0L
    var dataCells = 0L
    Using.resource(new StreamingReader(Files.newInputStream(source.toPath))) { reader =>
      reader.skipRows = 1
      reader.forEachRow { row =>
        dataRows += 1
        dataCells += row.length
      }
      onLive()
    }
    (dataRows, dataCells)
  }

  /** 传统 POI 全量载入读取：返回(数据行数, 单元格数)，减去表头行。 */
  private def readXssf(source: File, colCount: Int, onLive: () => Unit = () => ()): (Long, Long) = {
    val workbook = new XSSFWorkbook(source)
    try {
      val sheet = workbook.getSheetAt(0)
      onLive()
      var totalRows = 0L
      var totalCells = 0L
      val it = sheet.rowIterator()
      while (it.hasNext) {
        val row = it.next()
        totalRows += 1
        totalCells += row.getPhysicalNumberOfCells
      }
      (totalRows - 1, totalCells - colCount)
    } finally workbook.close()
  }

  test(s"streaming write/read efficiency on ${rows}*${cols} cells") {
    cols should be >= 20
    println(s"数据集: ${rows} 行 x ${cols} 列 = $cellCount 个单元格")

    // 写入
    val heapStart = heapMb
    val writeStart = System.nanoTime()
    writeStreaming(file, rows, cols)
    val writeMs = (System.nanoTime() - writeStart) / 1000000.0
    val writeTps = cellCount / (writeMs / 1000) / 10000
    val heapAfterWrite = heapMb

    file.exists() shouldBe true
    file.length() should be > 0L
    println(f"写入: $writeMs%6.0f ms, 文件大小 ${file.length() / 1048576.0}%5.2f MB, 吞吐 $writeTps%6.0f 万单元格/s")

    // 读取
    val readStart = System.nanoTime()
    val (dataRows, dataCells) = readStreaming(file)
    val readMs = (System.nanoTime() - readStart) / 1000000.0
    val readTps = cellCount / (readMs / 1000) / 10000

    dataRows should be(rows)
    dataCells should be(cellCount)
    println(f"读取: $readMs%6.0f ms, 读取 $dataRows 行 / $dataCells 单元格, 吞吐 $readTps%6.0f 万单元格/s")
    println(s"文件: ${file.getAbsolutePath}")
    println(s"堆内存: 写入前 $heapStart MB -> 写入后 $heapAfterWrite MB -> 读取后 ${heapMb} MB")
  }

  test(s"memory comparison on ${memRows}*${memCols} cells") {
    memCols should be >= 20
    val memCells = memRows.toLong * memCols
    println(s"内存对比: ${memRows} 行 x ${memCols} 列 = $memCells 单元格, 最大堆 ${maxHeapMb} MB")
    println("提示: XSSFWorkbook 为全量驻留内存；若默认 10000*20 仍 OOM，请调小 EXCEL_PERF_MEM_ROWS")

    val streamFile = tempFile("stream-write")
    val xssfFile = tempFile("xssf-write")

    // 1. 流式写 vs 全内存写
    // 驻留增量：基线与对象存活时点（onLive）均在强制 GC 后采样，差值近似该阶段的常驻占用
    var base = heapAfterGcMb
    var live = 0L
    var t0 = System.nanoTime()
    writeStreaming(streamFile, memRows, memCols, () => live = heapAfterGcMb)
    report("StreamingWriter 写入", (System.nanoTime() - t0) / 1000000.0, live - base)

    base = heapAfterGcMb
    live = 0L
    t0 = System.nanoTime()
    writeXssf(xssfFile, memRows, memCols, () => live = heapAfterGcMb)
    report("XSSFWorkbook  写入", (System.nanoTime() - t0) / 1000000.0, live - base)

    // 2. 流式读 vs 全量载入读（读同一个流式写出的文件）
    var sr1 = 0L
    var sc1 = 0L
    base = heapAfterGcMb
    live = 0L
    t0 = System.nanoTime()
    val r1 = readStreaming(streamFile, () => live = heapAfterGcMb)
    sr1 = r1._1
    sc1 = r1._2
    report("StreamingReader 读取", (System.nanoTime() - t0) / 1000000.0, live - base)

    var xr1 = 0L
    var xc1 = 0L
    base = heapAfterGcMb
    live = 0L
    t0 = System.nanoTime()
    val r2 = readXssf(streamFile, memCols, () => live = heapAfterGcMb)
    xr1 = r2._1
    xc1 = r2._2
    report("XSSFWorkbook  读取", (System.nanoTime() - t0) / 1000000.0, live - base)

    (sr1, sc1) shouldBe (memRows.toLong, memCells)
    (xr1, xc1) shouldBe (memRows.toLong, memCells)
    println(s"对比文件: ${streamFile.getAbsolutePath} / ${xssfFile.getAbsolutePath}")
  }

  private def report(label: String, ms: Double, extraHeap: Long): Unit = {
    println(f"$label%-22s${ms}%8.0f ms  驻留增量 $extraHeap%7d MB")
  }

  private def maxHeapMb: Long = Runtime.getRuntime.maxMemory() / (1024 * 1024)

  /** 强制 GC 后的稳定堆占用，单位 MB。 */
  private def heapAfterGcMb: Long = {
    val rt = Runtime.getRuntime
    System.gc()
    try Thread.sleep(100)
    catch { case _: InterruptedException => }
    (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
  }

  private def heapMb: Long = {
    var i = 0
    while (i < 3) { System.gc(); i += 1 }
    try Thread.sleep(200)
    catch { case _: InterruptedException => }
    val rt = Runtime.getRuntime
    (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
  }
}
