#!/usr/bin/env python3
"""统计 PDF 中 Type 3 字体的字形象素来源：位图还是矢量轮廓。

位图字形（多为字体内嵌点阵 EBDT/EBLC 被启用）会明显发虚，矢量轮廓则清晰。

用法:
    python3 pdf-glyph-report.py out.pdf [out2.pdf ...]
"""
import re
import sys
import zlib


def load_objects(data):
    objs = {}
    for m in re.finditer(rb'(\d+)\s+0\s+obj(.*?)endobj', data, re.S):
        objs[int(m.group(1))] = m.group(2)
    return objs


def stream_of(body):
    m = re.search(rb'stream\r?\n(.*?)\r?\nendstream', body, re.S)
    if not m:
        return b''
    raw = m.group(1)
    try:
        return zlib.decompress(raw)
    except Exception:
        return raw


def font_name(body, objs):
    fd = re.search(rb'/FontDescriptor\s+(\d+)\s+0\s+R', body)
    if not fd:
        return '?'
    m = re.search(rb'/FontName\s*/([A-Za-z+\-0-9]+)', objs.get(int(fd.group(1)), b''))
    return m.group(1).decode('latin-1') if m else '?'


def glyph_streams(body, objs):
    """返回 (glyph_name, decompressed_stream) 列表。"""
    ref = re.search(rb'/CharProcs\s+(\d+)\s+0\s+R', body)
    if ref:
        cp = objs.get(int(ref.group(1)), b'')
    else:
        inline = re.search(rb'/CharProcs\s*<<(.*?)>>', body, re.S)
        cp = inline.group(1) if inline else b''
    out = []
    for g in re.finditer(rb'/([A-Za-z0-9_.]+)\s+(\d+)\s+0\s+R', cp):
        out.append((g.group(1).decode('latin-1'), stream_of(objs.get(int(g.group(2)), b''))))
    return out


def classify(stream):
    """位图字模 = 矩形填充 + ExtGState(SMask)；矢量字形 = m/c/l 路径操作符。"""
    if re.search(rb'\bgs\b', stream) and re.search(rb'\bre\b', stream):
        return 'bitmap'
    if re.search(rb'\b[mcl]\b', stream):
        return 'path'
    return 'empty'


def report(path):
    try:
        data = open(path, 'rb').read()
    except OSError as e:
        print(f'== {path}\n   无法读取: {e}')
        return
    objs = load_objects(data)
    stats = {}
    for body in objs.values():
        if b'/Type3' not in body:
            continue
        name = font_name(body, objs)
        for gname, stream in glyph_streams(body, objs):
            if gname == 'g0':  # Skia 生成的占位字形
                continue
            s = stats.setdefault(name, {'bitmap': 0, 'path': 0, 'empty': 0})
            s[classify(stream)] += 1
    print(f'== {path}')
    if not stats:
        print('   没有 Type 3 字体（正文应为真实字体，正常）')
        return
    total = {'bitmap': 0, 'path': 0, 'empty': 0}
    for name, s in sorted(stats.items(), key=lambda kv: -(kv[1]['bitmap'] + kv[1]['path'])):
        print(f"   {name:<32} bitmap={s['bitmap']:<5} path={s['path']:<5} empty={s['empty']}")
        for k in total:
            total[k] += s[k]
    print(f"   {'TOTAL':<32} bitmap={total['bitmap']:<5} path={total['path']:<5} empty={total['empty']}")
    if total['bitmap']:
        print('   => 存在位图字模，这就是 PDF 发虚的原因，见 docs/pdf-cjk-font.md')


if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    for p in sys.argv[1:]:
        report(p)
