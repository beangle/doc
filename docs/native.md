# native image 支持（GraalVM）

## 概述

`beangle-doc` 的 `excel`、`docx` 两个模块直接依赖 Apache POI（`poi` / `poi-ooxml` /
`poi-ooxml-lite` / `xmlbeans`）。POI 通过 XMLBeans 编译出的 schema 类
（`org.openxmlformats.schemas.*`、`com.microsoft.schemas.*`）在运行期做反射创建与字段访问，
而 native image 是封闭世界分析，必须构建期注册；未注册时 GraalVM 25 默认直接抛
`MissingReflectionRegistrationError`，表现为导出 xlsx/docx 失败。

## 元数据文件

| 路径 | 内容 |
| --- | --- |
| `excel/src/main/resources/META-INF/native-image/poi-ooxml/reachability-metadata.json` | 2446 条反射 + 72 条资源 |
| `docx/src/main/resources/META-INF/native-image/poi-ooxml/reachability-metadata.json` | 与上一份逐字节一致 |

- **自动发现**：classpath 上 `META-INF/native-image/**/reachability-metadata.json` 会被
  native-image 自动合并，使用方无需任何构建参数（GraalVM 25 统一格式）。
- **为什么两个模块各放一份**：两个模块都直接依赖 `poi-ooxml`，而 `excel`/`docx` 到 `html`
  的依赖是 optional 的，不能保证使用方把 `html` 一起带上；把元数据放 `html` 会出现
  "只依赖 `excel` 时元数据静默丢失"。两份必须保持逐字节一致。
- 内容是 `poi-ooxml-lite` 的全量 schema 类，属于 POI 自身运行期反射面，与业务无关。

## 生成规则

- `*Impl` → `allDeclaredConstructors`
- `*$Enum` → `allDeclaredFields`
- 其余（接口等）→ 仅注册类名
- `resources`：`org/apache/poi/**/*.xsb`、`org/apache/xmlbeans/**/*.xsb`、
  `org/apache/poi/ss/formula/function/functionMetadata*.txt`，
  以及资源包 `org.apache.xmlbeans.impl.regex.message`

## 核实方式（可复现）

以 `poi 5.5.1` / `poi-ooxml 5.5.1` / `poi-ooxml-lite 5.5.1` / `xmlbeans 5.3.0` 为例：

1. **类型存在性**：收集这四个 jar 中全部 `.class` 的内名集合，逐个比对元数据里的 2446 个
   `type`，期望 0 缺失（2026-09-16 核实：0 缺失，无过期条目）。
2. **schema 校验**：用 GraalVM 25 的
   `reachability-metadata-schema-v1.2.0.json` 校验（`jsonschema.validate` 通过）；
   顶层只有 `comment`/`reflection`/`resources`/`foreign` 四个合法键。
3. **运行期验证**：`ems` 的 native 构建 + xlsx 导出（见 ems `docs/native-progress.md`
   第七、八节，含 AWT/字体测量相关配置）。

## 维护

- 升级 POI（`poi-ooxml-lite`）后需重新生成并核实：新增的 schema 类会缺注册（运行期报缺失），
  删除的类型会残留（构建期告警）。
- 社区元数据仓库 `oracle/graalvm-reachability-metadata` 目前**没有** `org.apache.poi` 条目，
  这份只能自备；其它库缺注册时可以去仓库里查现成配置（CC0，可整条抄用）。
- 若将来两个模块收敛出共同的、非 optional 的公共模块，可把两份合并为一份。
