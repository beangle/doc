# excel template 模块说明

## 概述

`org.beangle.doc.excel.template` 是 `beangle-doc-excel` 模块下的 Excel **模板渲染**子包（自 jxls 移植），解决“在既有 Excel 模板上按数据批量填充并导出”的问题：

- 模板即 `.xlsx` 文件，通过**单元格批注**书写指令（如 `jx:area`、`jx:each`），保持模板在 Excel/WPS 中可视化编辑；
- 单元格文本中可使用 `${表达式}` 占位符，也可使用 `$[公式]` 用户公式；
- 输出时按数据规模自动向下（或向右）扩展区域，并处理合并单元格、行高、列宽、超链接、批注、条件格式、图片等。

与 `org.beangle.doc.excel.stream`（流式写入/读取，面向大数据量、低内存）不同，template 子包面向**样式复杂、格式固定的报表导出**，以模板可维护性优先，内存与性能次之。

## 处理流程

1. `TransformHelper`/`DefaultTransformer.createTransformer` 加载模板 workbook，`SheetData`/`RowData`/`CellData` 将模板整表解析为内存模型（含样式、批注、合并、条件格式）。
2. `XlsCommentAreaBuilder.build()` 扫描单元格批注，解析出顶层 `jx:area` 区域与嵌套指令树，默认情况会先清空模板区（`clearTemplateCells`）。
3. 对每个区域调用 `Area.applyAt(cellRef, context)`：
   - 静态单元格原样复制到目标位置；
   - `jx:each` 等指令按数据逐项展开，`CellRange` 维护单元格/指令位移矩阵，展开后驱动后续指令与静态单元格下移/右移；
   - 复制时同步合并单元格、样式、行高/列宽、超链接、批注与条件格式。
4. 若开启公式处理（默认开启），`DefaultFormulaProcessor` 收集公式源单元格产生的所有目标单元格，重写公式中的相对引用（含 `U_(A1,B2)` 联合引用），使其在新位置语义正确；无引用可替换时回退到 `jx:params(defaultValue=...)` 或 `0`。
5. 多 sheet 模板（`jx:each(multisheet=...)`）删除模板页后输出；`setForceFormulaRecalculation(true)` 交由 Excel 打开时重算，`Transformer.write(os)` 写盘并释放资源。

## 模块组成

| 类 | 职责 |
| --- | --- |
| `TransformHelper` | 对外入口：加载模板 → 建区域 → 展开 → 处理公式 → 输出 |
| `DefaultTransformer` / `Transformer` | workbook 级操作：单元格复制、区域清除、公式设置、行高、合并、表格区域扩展 |
| `XlsCommentAreaBuilder` | 从批注解析顶层区域与嵌套指令，并把指令挂到所属最小区域 |
| `Area` / `DirectiveData` | 区域模型与位移矩阵：静态区复制、指令执行、指令依赖传播 |
| `CellData` | 单元格内存模型：类型、表达式求值、目标位置记录、写出 |
| `CellRange` | 区域内单元格相对坐标与“改动矩阵”，驱动 Shift 算法 |
| `FormulaProcessor` / `DefaultFormulaProcessor` | 公式改写：相对引用、`U_()` 联合引用、`BY_COLUMN` 策略、默认值 |
| `Context` | JEXL 表达式求值上下文（变量容器） |
| `RowData` / `SheetData` | 行/表模型（样式、列宽、合并、条件格式） |
| `directive/*` | 指令实现：`area`/`each`/`if`/`image`/`updateCell`/`mergeCells` |

## 模板语法

指令写在单元格**批注**中，每行一条，形如：

```
jx:area(lastCell="J3")
jx:each(items="departments", var="dep", lastCell="D4")
jx:if(condition="dep.size > 3", lastCell="D5", areas=["A5:D5"])
```

要点：

- 属性名与值之间用 `=`，**值必须带引号**（单引号/双引号均可，兼容若干 Unicode 引号）；无引号会被跳过并告警。
- `lastCell` 通常必填，用来确定指令作用区域；缺省时该指令被忽略（仅告警）。
- 支持 `areas=["A1:B2", ...]` 显式给出作用区域（用于 `jx:if` 的 then/else 分支、`jx:each` 的多段区域等）。
- 一个单元格批注可包含多行、多条指令。

### 指令一览

| 指令 | 主要属性 | 说明 |
| --- | --- | --- |
| `jx:area` | `lastCell` | 声明顶层模板区域（默认会先清空该区域） |
| `jx:each` | `items`、`var`、`lastCell`；可选 `direction`(down/right)、`select`、`groupBy`、`groupOrder`、`orderBy`、`multisheet`、`pageable`、`areas` | 循环展开；`direction="right"` 横向展开；`multisheet="sheetNamesVar"` 生成多页；`pageable` 自动分页（插入行分隔符） |
| `jx:if` | `condition`、`lastCell`、`areas` | 条件分支（空区域即不输出） |
| `jx:image` | `src`、`lastCell`；可选 `imageType`(PNG/JPG 等)、`scaleX`、`scaleY` | 输出图片，`src` 表达式求值为 `byte[]` 或 `InputStream` |
| `jx:updateCell` | `updater`、`lastCell` | 在复制前调用上下文中的 `CellDataUpdater` 自定义单元格数据 |
| `jx:mergeCells` | `lastCell`；可选 `rows`/`cols`/`minRows`/`minCols` | 复制后合并指定行列区域 |

### 表达式与用户公式

- 单元格文本 `${expr}`：用 JEXL 求值，结果按上下文类型写入（文本/数字/日期等）。
- 用户公式 `$[FORMULA]`：如 `$[B4*0.5+C4]`，会作为 Excel 公式写入，并在公式处理阶段按展开位置改写引用。
- 联合引用 `U_(cell1, cell2)`：用于公式中把多个分散单元格合并引用，如 `$[SUM(U_(F8,F13))]`。
- 参数注释：公式所在单元格批注可写 `jx:params(formulaStrategy="BY_COLUMN", defaultValue="0")`：
  - `formulaStrategy`：`DEFAULT`/`BY_COLUMN`/`BY_ROW`，控制展开后公式引用范围；
  - `defaultValue`：当公式没有可替换的引用（如循环无数据）时使用的值，缺省 `0`。

## 使用示例

模板：任意工作表中标注区域，例如 A1 批注 `jx:area(lastCell="D4")`，A3 批注 `jx:each(items="departments", var="dep", lastCell="D4")`，第 3 行放置表头，第 4 行放置 `${dep.name}` 等占位。

```scala
import org.beangle.doc.excel.template.TransformHelper
import java.io.FileOutputStream

val template = getClass.getClassLoader.getResourceAsStream("report_template.xlsx")
val out = new FileOutputStream("report.xlsx")
val helper = new TransformHelper(template)
val context = Map(
  "departments" -> departments,   // Seq[Department]
  "sheetNames"  -> departments.map(_.name) // jx:each(multisheet="sheetNames", ...) 时使用
)
helper.transform(out, context)
out.close()
```

输出内容验证（建议替换现有“只跑不校验”的测试风格）：

```scala
val wb = new XSSFWorkbook(new FileInputStream("report.xlsx"))
// 断言：行数 = 表头 + 数据条数；关键单元格文本/公式符合预期
```

## 常用开关

- `TransformHelper.deleteTemplateSheet`：多 sheet 模板输出后删除模板页（默认 true）。
- `TransformHelper.processFormulas`：是否执行公式改写（默认 true）。
- `Transformer.ignoreColumnProps` / `ignoreRowProps`：跳过列宽/行高复制。
- `DefaultTransformer.createTransformer(workbook, streaming = true)`：模板 workbook 用 `SXSSFWorkbook` 包装后输出（注意模板本身仍需整表读入，只是写出侧流式）。

## 已知边界与注意事项

以下问题已在复核后修复，并补充了回归测试：

- **区域内静态日期单元格会输出为空白**（已修复）：`CellData.updateCellContents` 的 `Date` 分支补上 `java.util.Date` 兜底，模板中既有的日期单元格可原样复制。
- **公式引用模板中不存在的行/列会 NPE**（已修复）：`FormulaProcessor.buildTargetCellRefMap`/`buildJointedCellRefMap` 对缺失单元格判空；普通引用回退保留原引用，联合引用跳过缺失项。
- **清除区域误删整个 sheet 的合并区域**（已修复）：`DefaultTransformer.removeMergedRegions` 改为只移除与区域相交的合并区域，`deleteTemplateSheet=false`（同 sheet 输出）时区域外的标题/脚注合并得以保留。
- **指令书写错误被静默忽略**（已缓解）：未知指令与构造失败的指令现在会输出 `error` 日志并附带原因（仍跳过该指令）；`lastCell` 缺失仍为告警。
- **批注扫描存在第 50 列上限**（已修复）：`getCommentedCells` 改为枚举 sheet 全量批注；`lastCommentedColumn` 保留为兼容字段，默认 `0` 表示不限列。
- **循环展开异常后上下文变量残留**（已修复）：`EachDirective.processCollection` 以 `finally` 恢复 `var`/`var_idx`；`groupBy` 未指定 `groupOrder` 时不再构造空排序器。
- **模板输入流泄漏**（已修复）：`TransformHelper.transform` 通过 `Using.resource` 关闭模板流；输出流仍由调用方负责。
- **测试覆盖偏薄**（已补充）：新增 `TransformerRegressionTest` 与 `TemplateRenderTest`：
  - `TransformerRegressionTest`：静态日期复制、缺失引用公式、区域外合并保留、50 列后批注发现；
  - `TemplateRenderTest`：对渲染输出做真实断言——`jx:each` 向下/向右展开、`jx:if` 条件过滤、标量/Bean 值替换、用户公式随行改写（含 shipped multisheet 模板的 `SUM(U_(...))` 汇总改写）、区域下方静态脚注行位移、`jx:each(multisheet=...)` 一页一条、合并区域逐行复制、条件格式逐行复制、`jx:updateCell` 改写、`jx:image` 嵌入图片。
- **`jx:updateCell` 修改字符串值被富文本回写覆盖**（已修复）：`CellData.updateStringCellContents` 原先只要求值结果等于 `cellValue` 就回写模板原始 `richTextString`，updater 改写的值不生效；现改为仅在求值结果等于**原单元格文本**时回写富文本（保留样式），改写值正常输出。

以下为仍存在的边界，使用前请评估：

- **条件格式按单元格逐条复制**：大规模 `jx:each` 展开仍可能产生大量规则；现已在单 sheet 规则数达 60000 时停止追加并告警，避免文件损坏。
- **JEXL 表达式无沙箱**：模板若来自不可信来源，`${...}` 等表达式存在被构造为任意方法调用的风险，建议仅处理受信任模板，或自行替换 `Context.evaluator` 为受限求值器。
- **属性值必须带引号**：指令属性如 `lastCell=B3` 不会被解析（仅告警），请统一写 `lastCell="B3"`。
- **枚举属性区分大小写**：如 `jx:each(direction="Right")` 才能生效；`direction="right"` 在指令属性拷贝阶段转换失败并被忽略（仅 error 日志）。
