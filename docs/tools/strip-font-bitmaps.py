#!/usr/bin/env python3
"""把 TTC/TTF 里的内嵌点阵表（EBDT/EBLC/EBSC）去掉，重建为纯矢量 TTF。

只删除位图表，字形轮廓、name/cmap 等全部原样保留（家族名仍是 SimSun/宋体），
因此替换后字形外观不变，只是不再出现点阵位图字形。

用法:
    python3 strip-font-bitmaps.py <in.ttc> <face-index> <out.ttf>

    # simsun.ttc 通常含两个字面：0=SimSun(宋体)  1=NSimSun(新宋体)
    python3 strip-font-bitmaps.py simsun.ttc 0 SimSun.ttf
    python3 strip-font-bitmaps.py simsun.ttc 1 NSimSun.ttf

需要"一步到位替换安装"时用 fix-font-bitmaps.py（会调用本文件的函数）。
"""
import struct
import sys

DROP = {b'EBDT', b'EBLC', b'EBSC', b'bdat', b'bloc'}


def face_offsets(data):
    """返回各字面在文件中的偏移。"""
    if data[:4] == b'ttcf':
        n = struct.unpack('>I', data[8:12])[0]
        return list(struct.unpack('>%dI' % n, data[12:12 + 4 * n]))
    return [0]


def table_dir(data, off):
    num = struct.unpack('>H', data[off + 4:off + 6])[0]
    return [list(struct.unpack('>4sIII', data[off + 12 + 16 * i:off + 28 + 16 * i]))
            for i in range(num)]


def bitmap_tables(data, base):
    """该字面带有的点阵表名列表。"""
    return [t[0].decode('latin-1') for t in table_dir(data, base) if t[0] in DROP]


def strip_face(data, base):
    """返回 (重建后的 TTF 字节, 被删除的表名列表, 保留的表数量)。"""
    tabs = table_dir(data, base)
    dropped = [t[0].decode('latin-1') for t in tabs if t[0] in DROP]
    keep = sorted((t for t in tabs if t[0] not in DROP), key=lambda t: t[0])
    num = len(keep)
    search_range_exp = max(1, num).bit_length() - 1
    out = bytearray(struct.pack('>IHHHH', 0x00010000, num,
                                16 * (1 << search_range_exp), search_range_exp,
                                num * 16 - 16 * (1 << search_range_exp)) + b'\0' * 16 * num)
    offset = 12 + 16 * num
    for i, (tag, checksum, toff, tlen) in enumerate(keep):
        if offset % 4:
            offset += 4 - offset % 4
        blob = data[toff:toff + tlen]
        out += blob
        struct.pack_into('>4sIII', out, 12 + 16 * i, tag, checksum, offset, tlen)
        offset += len(blob)
        while offset % 4:
            out += b'\0'
            offset += 1
    return bytes(out), dropped, num


def main(src, idx, dst):
    try:
        data = open(src, 'rb').read()
    except OSError as e:
        sys.exit(f'{src}: 无法读取 ({e})')
    offsets = face_offsets(data)
    if int(idx) >= len(offsets):
        sys.exit(f'{src} 只有 {len(offsets)} 个字面，index {idx} 越界')
    blob, dropped, num = strip_face(data, offsets[int(idx)])
    if not dropped:
        print(f'提示: {src}[{idx}] 没有点阵表，仍会重建输出。')
    try:
        open(dst, 'wb').write(blob)
    except OSError as e:
        sys.exit(f'{dst}: 无法写入 ({e})')
    print(f'{src}[{idx}] -> {dst}: 保留 {num} 张表, 删除 {dropped}')


if __name__ == '__main__':
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2], sys.argv[3])
