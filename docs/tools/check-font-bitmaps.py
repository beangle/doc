#!/usr/bin/env python3
"""检查字体文件是否带内嵌点阵字模（EBDT/EBLC/EBSC/CBDT/CBLC）。

带点阵的字体（典型：simsun.ttc）会让 Chromium/Skia 在小字号时输出位图字形，
PDF 正文就会发虚。去掉这些表即可强制走矢量轮廓，见
strip-font-bitmaps.py。

用法:
    python3 check-font-bitmaps.py                # 自动检查 fc-match 解析出的 宋体
    python3 check-font-bitmaps.py /path/font.ttc [font2.ttf ...]
    python3 check-font-bitmaps.py /usr/share/fonts   # 传目录时递归扫描其中的字体文件

兼容 Python 3.6+（服务器上常见的 python36 也能直接跑）。
"""
import os
import struct
import subprocess
import sys

BITMAP_TABLES = {'EBDT', 'EBLC', 'EBSC', 'CBDT', 'CBLC', 'bdat', 'bloc'}
FONT_SUFFIXES = ('.ttf', '.ttc', '.otf', '.otc', '.pcf', '.pfb')


def family_files(family='宋体'):
    """用 fc-match 解析族名对应的字体文件，取第一个匹配。"""
    try:
        # 注意: capture_output/ text 是 Python 3.7+ 才有，这里用 3.6 也支持的写法
        out = subprocess.check_output(['fc-match', '-f', '%{file}\n', family],
                                      universal_newlines=True)
    except (OSError, subprocess.CalledProcessError):
        return []
    files = [line.strip() for line in out.splitlines() if line.strip()]
    return files[:1]


def faces(data):
    """返回 [(index, table_tags)]。"""
    if data[:4] == b'ttcf':
        n = struct.unpack('>I', data[8:12])[0]
        offsets = struct.unpack('>%dI' % n, data[12:12 + 4 * n])
    else:
        offsets = (0,)
    result = []
    for i, off in enumerate(offsets):
        num = struct.unpack('>H', data[off + 4:off + 6])[0]
        tags = []
        for j in range(num):
            rec = off + 12 + 16 * j
            tags.append(data[rec:rec + 4].decode('latin-1'))
        result.append((i, tags))
    return result


def expand(targets):
    """把目录展开成其中的字体文件列表。"""
    files = []
    for t in targets:
        if os.path.isdir(t):
            for root, dirs, names in os.walk(t):
                for n in sorted(names):
                    if n.lower().endswith(FONT_SUFFIXES):
                        files.append(os.path.join(root, n))
        else:
            files.append(t)
    return files


def check(path):
    """返回 True(有点阵) / False(无点阵) / None(文件不可读)。"""
    try:
        data = open(path, 'rb').read()
    except OSError as e:
        print(f'{path}: 无法读取 ({e})')
        return None
    found = False
    print(f'== {path}')
    for idx, tags in faces(data):
        hits = [t for t in tags if t in BITMAP_TABLES]
        print(f'   face[{idx}] 内嵌点阵表: {hits if hits else "无"}')
        found = found or bool(hits)
    return found


if __name__ == '__main__':
    targets = expand(sys.argv[1:] or family_files())
    if not targets:
        sys.exit('未指定字体文件，且 fc-match 解析 宋体 失败')
    print(f'共 {len(targets)} 个字体文件待检查\n')
    # 注意不能写成 any(check(p) for p in targets)：any 短路会漏检后面的文件
    results = [(p, check(p)) for p in targets]
    print()
    bitmap_fonts = [p for p, r in results if r is True]
    if bitmap_fonts:
        print('存在内嵌点阵的字体:')
        for p in bitmap_fonts:
            print(f'   {p}')
        print('结论: 这些字体在 Chromium 输出 PDF 时可能出现位图字形（发虚），')
        print('      建议用 strip-font-bitmaps.py 去掉点阵，或替换为无点阵的等价字体')
    elif any(r is False for _, r in results):
        print('结论: 没有内嵌点阵，PDF 不会是点阵字形')
    else:
        print('结论: 无法判断，文件都不可读')
