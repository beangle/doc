# chrome-headless-shell 与 beangle-doc 集成

服务器安装、各发行版差异和 CentOS 7 说明见 [chrome-headless-shell 安装指南](https://duantihua.github.io/tech/linux/chrome-headless-shell-install.html)。本文说明 `beangle-doc` pdf 模块如何查找和启动 `chrome-headless-shell`。

`pdf/src/main/scala/org/beangle/doc/pdf/cdt/ChromeLauncher.scala` 已完成适配。

## 1. 查找可执行文件

`findChrome()` 只查找 `chrome-headless-shell`，不再回退到完整 Chrome/Chromium。查找顺序为：

1. 环境变量 `CHROME_HEADLESS_SHELL_PATH`
2. `/opt/chrome-headless-shell/chrome-headless-shell-linux64/chrome-headless-shell`
3. `/usr/bin/chromium-headless-shell`（Debian 包）
4. `/usr/lib/chromium/chromium-headless-shell`（Debian 包）
5. `/usr/lib64/chromium-browser/headless_shell`（Fedora/EPEL 的 chromium-headless 包）

按安装指南把 shell 装到 `/opt/chrome-headless-shell` 后，程序无需额外配置即可直接使用。

## 2. 启动参数

`defaultsArgs()` 统一不传 `--headless`，因为 shell 本身永远是无头模式；其余渲染参数保持一致。

shell 与完整 Chrome 的默认值不同，以下参数必须显式指定：

### 2.1 `--font-render-hinting=none`

shell 的默认渲染结果等价于 `--font-render-hinting=medium`/`full`（三者在 300dpi 下逐像素相同），而 Chrome 的默认结果等价于 `none`/`slight`。

同一页面实测（两者都是 Skia/PDF m152，排除版本差异）：

- shell 默认 vs Chrome：有 5018 个像素不同（0.06%），30 个词里 10 个词坐标不同（步进与断行漂移）。
- 加上该参数后：shell 与 Chrome **逐像素一致、词坐标完全相同**。

hinting 会改写字形轮廓与字符步进，所以不加会出现“同一段文字宽度不同、断行漂移”，小字号尤其发闷。注意这与宋体点阵无关：该对照页用的是 Noto CJK，全部是矢量字形。

### 2.2 `--force-color-profile=srgb`

把默认色彩配置显式化。headless 默认**已经是 sRGB**——实测同一个彩色页面加不加该参数输出逐字节相同（`md5` 一致），改用 `--force-color-profile=display-p3-d65` 才会改变输出。

因此它只是防止宿主或未来版本改变默认值的保险，本身并不“修复”任何颜色问题。

## 3. 相关文档

- 安装与发行版说明：[chrome-headless-shell 安装指南](https://duantihua.github.io/tech/linux/chrome-headless-shell-install.html)
- 中文字体导致的 PDF 发虚：[pdf-cjk-font.md](pdf-cjk-font.md)
