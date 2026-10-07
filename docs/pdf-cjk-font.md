# Linux 下宋体（SimSun）生成 PDF 发虚

面向 `beangle-doc` 的 pdf 模块（`org.beangle.doc.pdf`）在 Linux 服务器上通过 Chrome / chrome-headless-shell 渲染 HTML 为 PDF 的场景。

相关文档：`chrome-headless-shell-install.md`（二进制安装、shell 与 Chrome 的参数一致性）；本文只讲**中文字体导致的 PDF 质量问题**。

## 1. 现象

- 正文看着**发虚**，像老式屏幕点阵字：笔画边缘有灰块、锯齿，放大后更明显。
- 字形"**不太对**"：笔画粗细和结构跟印刷体不一样（点阵字模是为小字号屏幕显示单独设计的，和字体轮廓不是同一套字形）。
- **粗体（标题）比正文更糊**，同一页里 `楷体` 却是清晰的。
- `pdffonts` 里 `SimSun` 是 `Type 3`，而且数量很多（每个字模一个字体对象）；正常应该是 `CID TrueType`。

## 2. 结论

**不是 chrome-headless-shell 的问题，是字体自带的点阵字模被用上了。**

`simsun.ttc` 内置了 6 个内嵌点阵 strike（`EBDT`/`EBLC`，ppem 12~17）。Chromium/Skia 在字号命中 strike 时会**直接输出位图字形**，而不是矢量轮廓；配合`宋体没有 Bold 字面`带来的合成粗体，位图还会被直接放大，所以最糊。

去掉字体里的 `EBDT`/`EBLC` 即可根治，**与浏览器版本无关**，且字形轮廓完全不变（见第 5 节）。

## 3. 快速判定

```bash
# 1) 字体里有没有点阵表
python3 docs/tools/check-font-bitmaps.py          # 默认检查 fc-match 宋体 的结果
#   face[0] 内嵌点阵表: ['EBDT', 'EBLC']   <= 命中

# 2) PDF 里的字形是位图还是矢量
python3 docs/tools/pdf-glyph-report.py /tmp/out.pdf
#   EAAAAA+SimSun   bitmap=555   path=0     <= 位图 = 发虚
#   DAAAAA+KaiTi    bitmap=0     path=17    <= 矢量 = 正常
```

`bitmap` 大于 0 基本就确诊了。位图字模的 charproc 形如
`4 0 0 4 44 -76 cm /G1060 gs 0 0 16 16 re f`（16×16 矩形填一层灰度掩膜）；
矢量字形则是 `75 -187 m 185 -187 l ... f`。

## 4. 根因

### 4.1 字体本身带点阵

`simsun.ttc`（以及 `NSimSun`/`新宋体`）带 `EBDT`/`EBLC` 表，即 Windows 时代为小字号屏幕显示准备的**内嵌点阵字模**，ppem 覆盖 12~17。这是 Windows 字体的一贯做法（还有 MingLiU、MS Gothic、Cambria、Calibri 等）。

### 4.2 Chromium/Skia 会优先用点阵

Skia 的 FreeType 后端在允许内嵌点时，把 `FT_LOAD_NO_BITMAP` 去掉，此时 FreeType 在命中 strike 的字号上返回的就是**位图 glyph**（本地实测：12pt/96dpi 即 16ppem，返回 `FT_GLYPH_FORMAT_BITMAP`）。Skia 拿到位图后无法把它塞进普通 PDF 字体，只能退化成 `Type 3` 位图字形。

页面里 `font-size: 12pt`（=96dpi 下 16px）正好踩在 strike 上，所以正文中招。

### 4.3 为什么粗体最明显

`simsun.ttc` 只有 Regular，没有 Bold 字面。`font-weight:bold` 于是走**合成粗体**（浏览器把字形加粗再输出），位图字模被直接拉伸放大，糊得更厉害。"粗体标题明显比正文差"就是典型特征。

### 4.4 为什么改 fontconfig 没用

Chromium/Skia **不使用 fontconfig 的 `embeddedbitmap` 设置**，`/etc/fonts/local.conf` 里写 `embeddedbitmap=false` 不起作用。实测：加完配置重启 Java 后新生成的 PDF 与原 PDF **仅时间戳不同**（大小同为 1,999,121 字节，300dpi 像素差为 0）。

### 4.5 与浏览器版本的关系（不是 shell 的锅）

同一台机器上，`chrome-headless-shell` 与完整 Chrome 输出**逐字节同构**；而不同版本之间会有差异：

| 环境 | 浏览器 | 同一份 HTML 的 SimSun 字形 |
| --- | --- | --- |
| 服务器 | chrome-headless-shell `151.0.7922.34`（Skia/PDF m151） | `bitmap=735  path=29`（位图） |
| 本机 | Chromium `152.0.7977.75` / Chrome `152.0.7977.82` | `bitmap=0  path=206`（矢量） |

所以"升级到 shell 之后变差"这个判断不一定成立：**先按第 3 节判定字形象素来源**，再决定是换浏览器还是改字体。不管哪个版本，去掉字体点阵都能根治。

## 5. 修复：去掉字体里的点阵（推荐）

原理：删掉 `EBDT`/`EBLC` 后 FreeType 只能返回矢量轮廓，PDF 必然是矢量字形。

安全性：只删位图表，`glyf`/`cmap`/`name`/`hmtx` 等原样保留，**字形轮廓与排版完全不变**。实测替换前后同一页面 278 个 Type 3 charproc **逐字节相同**（sha256 一致），只是不再出现位图。

### 5.1 一步到位（推荐）

`docs/tools/fix-font-bitmaps.py` 把 5.2 节的手工步骤合成一条命令：导出无点阵 TTF → 装进扫描目录 → 刷新缓存 → 清掉**同族**的多余文件（含 `.bak`）→ 再刷新 → 校验「一个族只剩一份」。

```bash
python3 docs/tools/fix-font-bitmaps.py --dry-run   # 先看计划：写什么、删/移什么
sudo python3 docs/tools/fix-font-bitmaps.py --yes   # 确认无误后执行；--yes 跳过交互确认
```

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `--source FILE` | `fc-match 宋体` 的第一个结果 | 源字体；ttc 里的每个字面都会导出 |
| `--dest-dir DIR` | `/usr/local/share/fonts/simsun` | 新 TTF 安装目录，必须在 fontconfig 扫描路径内（不在就中止） |
| `--mode trash\|move` | `trash` | 原字体处理：`trash` 直接删除；`move` 移到 `--backup-dir` |
| `--backup-dir DIR` | `/root/font-backup` | 仅 `move` 使用；落在扫描路径内会被拒绝 |
| `--yes` / `--dry-run` / `--force` | — | 跳过确认 / 只打印计划 / 本来没点阵也执行 |

执行时会逐个字面打印保留与删除了哪些表；再按字体族用 `fc-list -f '%{file}\n' :family=<族名>` 找出**同族的所有文件**（这正是 `.bak` 陷阱），连同源文件一起按 `--mode` 处理；最后校验每个族只剩新装的那一份，并打印重启 Java 的提示与对应模式的回滚命令。

> 默认 `trash` **删除**原 `simsun.ttc` 是有意的：同族只要存在第二份文件，谁生效就取决于排序，随时可能翻回点阵。想留原件用 `--mode move --backup-dir /root/font-backup`（该目录在扫描路径之外）。

### 5.2 手工步骤（脚本做的事）

```bash
# 0. 先看是什么字体、有没有点阵
python3 docs/tools/check-font-bitmaps.py
fc-match -f '%{file}\n' 宋体

# 1. 把每个字面导出为不含点阵的 TTF（simsun.ttc: 0=SimSun 1=NSimSun）
#    注意 fc-match 可能输出多行，取第一行
TTC=$(fc-match -f '%{file}\n' 宋体 | head -n 1)
mkdir -p /usr/local/share/fonts/simsun
python3 docs/tools/strip-font-bitmaps.py "$TTC" 0 /usr/local/share/fonts/simsun/SimSun.ttf
python3 docs/tools/strip-font-bitmaps.py "$TTC" 1 /usr/local/share/fonts/simsun/NSimSun.ttf

# 2. 把带点阵的原字体移出 fontconfig 扫描目录，刷新缓存
#    注意：只是改名成 xxx.bak 不行！fontconfig 不按扩展名过滤，仍会把它当字体加载，
#    结果同一个「宋体」有两份文件，谁生效取决于排序，随时可能翻转。
mkdir -p /root/font-backup
mv "$TTC" /root/font-backup/
fc-cache -f

# 3. 确认「宋体/新宋体」各自只剩下新目录里的那一份，且不再有点阵
fc-match -f '%{file}\n' 宋体
fc-list -f '%{file}\n' :family=宋体   | sort -u    # 应只有 SimSun.ttf
fc-list -f '%{file}\n' :family=新宋体 | sort -u    # 应只有 NSimSun.ttf
python3 docs/tools/check-font-bitmaps.py /usr/local/share/fonts/simsun/SimSun.ttf
```

> 不要只删 `.ttc` 文件而不导出 TTF，否则 `宋体` 会退化成别的字体（字形全变）。
>
> 也不要把备份留在 `/usr/share/fonts`、`/usr/local/share/fonts`、`~/.fonts` 等**任何 fontconfig 会扫描的目录**里——`.bak`、加前缀点（`.simsun.ttc.bak`）都照扫不误。实在要放本机，放到 `/root/font-backup/` 这类目录。

**改完必须重启 Java 应用**：常驻 Chrome 进程在启动时缓存字体列表，不重启不生效；`PdfMakerService` 的 idle 超时（默认 5 分钟）回收进程后重建也等效。

## 6. 验证

```bash
# 重新生成一份 PDF
python3 docs/tools/pdf-glyph-report.py /tmp/after.pdf   # 应为 bitmap=0，全部 path
pdffonts /tmp/after.pdf | grep -i simsun                # Type 3 应大幅减少或消失
```

替换字体前后轮廓不变，所以版面不会漂移。若要对比像素，直接和"本来就是矢量输出"的基准比即可。

## 7. 其他方案与权衡

| 方案 | 做法 | 权衡 |
| --- | --- | --- |
| **去点阵（推荐）** | 本文第 5 节 | 字形、排版完全不变；需要改字体文件并重启应用 |
| 换等价字体 | fontconfig 用 `<match>` 把 `宋体` 强替换为无点阵的宋体类字体（`Noto Serif CJK SC`/思源宋体） | 不动字体文件，但字形与宋体有肉眼可见差异 |
| 去掉合成粗体 | 页面或 `PrintOptions` 里禁用合成粗体（`font-synthesis-weight:none`），或给粗体指定真有粗字面的字体 | 只解决粗体部分，且会丢失粗体效果 |
| 升级浏览器 | 换到 chrome-headless-shell 152 再比对 | 未必有效，且版本漂移本身会影响排版 |

## 8. FAQ

**Q：`fc-match -f '%{file}\n' 宋体` 输出好几行怎么办？**
A：`宋体`/`SimSun` 一个 ttc 里可能有多个字面，`$TTC` 会拿到多行路径。统一用 `| head -n 1`。

**Q：为什么同一页的`楷体`没事？**
A：`KaiTi` 没有内嵌点阵 strike，FreeType 只能给轮廓，所以一直是矢量（实测 `DAAAAA+KaiTi bitmap=0 path=17`）。

**Q：改完需要重启 Java 吗？**
A：需要。Chrome 进程启动时缓存字体列表。除了重启 Java，等 `PdfMakerService` 的 idle 超时回收重建进程也等效。

**Q：网页显示会受影响吗？**
A：会。小字号中文会从"屏幕点阵字"变成矢量渲染，看起来更细、更"印刷体"——这正是 PDF/打印想要的效果；印刷和小字号屏幕显示本来就是两套字形。

**Q：只影响 PDF 吗？**
A：位图字模是字体层面的，浏览器页面渲染同样会用；只是屏幕上小字号点阵本来"显得清楚"，而 PDF 会被放大/打印，就露馅了。

**Q：服务器上没有 python3（或只有 3.6）？**
A：`docs/tools/` 下四个脚本按 **Python 3.6+** 编写、无第三方依赖，不用 3.7+ 才有的 `subprocess(capture_output=...)`/`text=` 参数，RHEL/CentOS 的 `/usr/lib64/python3.6` 可直接跑。

**Q：怎么回滚？**
A：关键是**别让同一个族留下两份文件**。`--mode move` 装完脚本会直接打印对应命令，形如
`mv /root/font-backup/simsun.ttc /usr/share/fonts/chinese/ && rm /usr/local/share/fonts/simsun/*.ttf && fc-cache -f`，然后重启 Java。
默认的 `--mode trash` 已把原件删除，回滚需要先取回原 `simsun.ttc` 放回原位，并删掉新装的 TTF。

**Q：`fc-list` 里出现两份 SimSun（比如原来的 `xxx.bak` 和新 TTF）会怎样？**
A：fontconfig 不看扩展名，`.bak` 也会被注册成字体，于是同一个族有两份文件，**谁生效取决于 fontconfig 的目录/文件名排序**，可能今天对、换台机器或重新 `fc-cache` 后又变回位图。必须保证一个族只有一份文件：
`fc-list -f '%{file}\n' :family=宋体 | sort -u` 只应输出一行。
`fix-font-bitmaps.py` 会自动把同族的 `.bak` 一起清掉（`trash` 删除 / `move` 移走），一般不用再手工处理。

**Q：其它字体也想排查？**
A：`python3 docs/tools/check-font-bitmaps.py /usr/share/fonts` 会递归扫描目录并列出所有带点阵的字体（`MingLiU`、`MS Gothic`、`Cambria`、`Calibri` 等老字体常见）。

## 9. 工具脚本

`docs/tools/` 下四个脚本都是纯标准库，可直接 `scp` 到服务器：

| 脚本 | 作用 |
| --- | --- |
| `pdf-glyph-report.py` | 统计 PDF 里 Type 3 字形的位图/矢量构成，判断"发虚"是否来自点阵 |
| `check-font-bitmaps.py` | 检查字体文件或目录是否带 `EBDT/EBLC` 等点阵表（默认检查 `fc-match 宋体`） |
| `strip-font-bitmaps.py` | 去掉点阵表重建纯矢量 TTF，家族名与字形轮廓保持不变 |
| `fix-font-bitmaps.py` | 一步到位：导出无点阵 TTF + 安装 + 清理同族残留（含 `.bak`）+ 校验；内部调用 `strip-font-bitmaps.py` |

文中命令按"脚本位于本仓库 `docs/tools/`、在当前目录执行"书写，服务器上按脚本实际路径调整。
