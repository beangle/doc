# excel stream 模块说明

## 概述

`org.beangle.doc.excel.stream` 是 `beangle-doc-excel` 模块下的流式读写子包，面向**大体积 .xlsx 文件**的低内存读写场景：

- **写入**：基于 Apache POI `SXSSFWorkbook`（滑动窗口 + 临时文件刷盘），内存中只保留最近 N 行。
- **读取**：基于 POI `XSSFReader` + `ReadOnlySharedStringsTable` + StAX 逐行解析 sheet XML，内存中只保留当前行。

两者对称设计，配合使用可将数十万行乃至百万行级别的 Excel 读写控制在稳定内存内。

## 模块组成

| 类 | 职责 |
| --- | --- |
| `StreamingWriter` | 流式写入入口，管理 workbook、自动换 sheet，委托 `StreamingSheet` 写行 |
| `StreamingSheet` | 单个 sheet 的写操作：表头、标题行（合并单元格）、数据行、列宽、冻结窗格 |
| `StreamingReader` | 流式读取入口，按 sheet 顺序逐行解析，支持跳行、类型转换、读取单元格注释 |

配合使用的既有能力（不在 stream 包内）：

- `CellOps.fillin`：`writeRow` 时按值的实际类型自动识别并写入（字符串、日期、时间、数字等）。
- `ExcelStyleRegistry`：按 `DataType` 注册默认显示格式（日期、小数、百分比等）。
- `org.beangle.commons.io.DataType`：读写两侧共用的类型枚举。

## StreamingWriter

### 构造参数

```scala
new StreamingWriter(windowSize: Int = 100, countPerSheet: Int = 100000)
```

- `windowSize`：SXSSF 滑动窗口行数，超过后自动刷到磁盘临时文件（已开启 `compressTempFiles`）。
- `countPerSheet`：每张 sheet 写入的数据行上限，达到后自动新建 sheet（构造时已确保存在一个默认 sheet）。

### API 一览

| 方法 | 说明 |
| --- | --- |
| `writeHeaders(String*)` | 写表头行，默认加粗居中样式 |
| `writeHeaders(Seq[String], style, widths, rowHeight)` | 写表头行，可指定样式、列宽、行高 |
| `writeCaption(text, colCount, style, addBorder)` | 写跨列合并的标题行 |
| `writeRow(values: Any*)` | 写一行数据，值按类型自动识别（见下方类型规则），支持链式调用 |
| `setColumnWidths(Int*)` | 按字符数设置列宽 |
| `freezePane(colSplit, rowSplit)` | 冻结窗格 |
| `changeSheetName(name)` | 重命名当前 sheet |
| `getCurrentRowNum` | 当前活跃 sheet 的行号（0-based） |
| `createCellStyle()` / `createFont()` | 创建自定义样式/字体（用于表头、标题行） |
| `save(path/File/OutputStream)` | 输出 xlsx；`save` 成功后内部会关闭 workbook，之后不能再继续写入 |
| `close()` | 关闭并释放资源 |

### 类型自动识别规则（写入时）

| 传入值 | 写入结果 | 默认格式 |
| --- | --- | --- |
| `String` | 文本 | `@` |
| `Boolean` | 文本 `Y` / `N` | `@` |
| `Int` / `Long` / 其他 `Number` | 数字 | `0`（`Integer`） |
| `Float` / `Double` | 数字 | `#,##0.##` |
| `LocalDate` / `java.sql.Date` | 日期单元格 | `yyyy-MM-dd` |
| `LocalDateTime` / `Instant` / `ZonedDateTime` | 日期时间单元格 | `yyyy-MM-dd hh:mm:ss` |
| `LocalTime` / `java.sql.Time` | 时间单元格 | `hh:mm:ss` |
| `YearMonth` / `MonthDay` | 日期单元格 | `yyyy-MM` / `MM-dd` |
| `null` / `None` | 空单元格 | — |

## StreamingReader

### 构造参数

```scala
new StreamingReader(is: InputStream, sheetNum: Int = 0)
```

`sheetNum` 从 0 开始；构造时内部解析到目标 sheet，读取操作只针对该 sheet。

### API 一览

| 方法/属性 | 说明 |
| --- | --- |
| `readRow(): Option[Array[Any]]` | 顺序读取下一行，读尽返回 `None` |
| `readRow(types: Array[DataType])` | 读取下一行并按列类型转换 |
| `forEachRow(callback)` | 顺序遍历所有数据行（会遵守 `skipRows`） |
| `skipRows: Int` | 需跳过的行数（如模板注释行/表头），默认 0 |
| `currentRowNum` | 当前已读取行数（0-based，实际读取计数） |
| `comments: Map[String, String]` | 单元格注释 `cellRef -> 注释文本`（首次调用解析 `xl/commentsN.xml`） |
| `getSheetName` | 当前 sheet 名称 |
| `close()` | 关闭 StAX reader、sheet 流与 OPCPackage |

### 单元格解析规则（读取时）

| xlsx 单元格类型 | 返回的 Scala 值 |
| --- | --- |
| 共享字符串 `t="s"` | 通过 `ReadOnlySharedStringsTable` 还原为 `String` |
| 布尔 `t="b"` | `Boolean` |
| 数字（无类型标识，含日期样式单元格） | `Double` |
| 空字符串/空值 | `null` |
| 公式结果、富文本等 | 视底层 XML 为普通文本/数字处理 |

`readRow(types)` 的类型转换支持：`String` / `Integer` / `Long` / `Float` / `Double` / `Boolean`。
其中 `Boolean` 转换能识别 `true` / `1` / `Y`，与写入侧 `Boolean -> Y/N` 正好闭合。

### 使用提示

- 行数据按 XML 中 `<c>` 的出现顺序存放，**不携带列号**；空白/无 `<c>` 的单元格会缺失，因此 `Array` 长度可能小于表宽，适合顺序遍历而非按坐标随机访问。
- 读取不感知单元格显示格式：**日期样式单元格读回的是 Excel 序列号 `Double`**（如需还原日期，需结合列样式或模板注释自行转换）。
- `comments` 解析按 `sheetNum + 1` 查找 `xl/commentsN.xml`；注释文本形如 `姓名:String`、`出生日期:Date`，可用作表头/属性定义的元数据，配合 `skipRows` 跳过注释行。

## 典型用法

### 写入

```scala
import org.beangle.doc.excel.stream.StreamingWriter

val writer = new StreamingWriter()
try {
  writer.writeHeaders("姓名", "年龄", "入职日期", "工资")
  employees.foreach { e =>
    // String / Int / LocalDate / Double 自动识别类型与显示格式
    writer.writeRow(e.name, e.age, e.onboardDate, e.salary)
  }
  writer.save("/tmp/employees.xlsx") // save 后 workbook 已关闭
} catch { case t: Throwable => writer.close(); throw t }
```

### 读取

```scala
import org.beangle.doc.excel.stream.StreamingReader

val reader = new StreamingReader(Files.newInputStream(Paths.get("/tmp/employees.xlsx")))
try {
  reader.skipRows = 1 // 跳过表头
  reader.forEachRow { row =>
    println(row.mkString(" | "))
  }
} finally reader.close()
```

## 测试与性能

`../excel/src/test/scala/org/beangle/doc/excel/stream` 下有两个测试：

- `StreamingReaderTest`：功能测试（顺序读、`forEachRow`、类型转换、`skipRows`、`currentRowNum`、注释解析），测试数据在内存中动态生成。
- `StreamingPerformanceTest`：包含两个用例（字符串/日期/数字混合数据）：
  - 效率测试：默认 **50,000 行 × 20 列 = 1,000,000 单元格**，先 `StreamingWriter` 写盘再 `StreamingReader` 读回，输出耗时、吞吐与文件大小。
  - 内存对比：默认 **10,000 行 × 20 列 = 200,000 单元格**，同一数据分别用 `StreamingWriter`/`XSSFWorkbook` 写、`StreamingReader`/`XSSFWorkbook` 读，在对象存活时点（强制 GC 后）采样驻留堆增量。

本机参考数据（会随机器与 JDK 波动）：

| 阶段 | 耗时 | 吞吐 |
| --- | --- | --- |
| 写入 1M 单元格 | 约 3.1 s（文件约 8.9 MB） | 约 32 万单元格/s |
| 读取 1M 单元格 | 约 1.8 s | 约 56 万单元格/s |

内存对比参考（200,000 单元格，测试 JVM 最大堆 1024 MB）：

| 阶段 | 驻留堆增量 |
| --- | --- |
| `StreamingWriter` 写入 | 约 2–7 MB |
| `XSSFWorkbook` 写入（全量驻留） | 约 145 MB |
| `StreamingReader` 读取 | 约 11 MB |
| `XSSFWorkbook` 读取（全量载入） | 约 136 MB |

流式方式内存几乎与数据量无关（只保留窗口行/当前行）；传统 `XSSFWorkbook` 全量方式与单元格数量成正比。
在 1 GB 堆下用 `XSSFWorkbook` 全量写入 1,000,000 单元格会触发 `OutOfMemoryError`，而 `StreamingWriter` 可以正常完成——这也是该模块存在的意义。

运行方式：

```bash
# 功能测试
sbt 'excel/testOnly org.beangle.doc.excel.stream.StreamingReaderTest'

# 性能测试（默认 50000*20）
sbt 'excel/testOnly org.beangle.doc.excel.stream.StreamingPerformanceTest'

# 只跑内存对比用例（默认 10000*20）
sbt 'excel/testOnly org.beangle.doc.excel.stream.StreamingPerformanceTest -- -z "memory comparison"'
```

性能测试可用环境变量调整规模与输出路径（`EXCEL_PERF_ROWS` / `EXCEL_PERF_COLS` / `EXCEL_PERF_FILE`），
内存对比可用 `EXCEL_PERF_MEM_ROWS` / `EXCEL_PERF_MEM_COLS` 调整（传统 `XSSFWorkbook` 全量载入受堆内存限制，默认取 10,000 行）；也可用系统属性 `excel.perf.*`（优先级更高）。

## 已知约束与设计取舍

- 仅支持 `.xlsx`（OOXML），不支持老的 `.xls`。
- 读取为**单 sheet 顺序流**：定位到目标 sheet 后从头到尾消费，不可回退/随机跳转。
- 写入侧 `save` 即关闭，输出大文件时建议先落临时文件再移动/上传。
- 读取不保留空单元格与列坐标、不做日期还原；复杂表格建议用 `comments` + `skipRows` 声明表头/类型后配合 `readRow(types)` 使用。
