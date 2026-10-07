#!/usr/bin/env python3
"""一步到位：把带内嵌点阵的字体（典型 simsun.ttc）替换成纯矢量 TTF 并安装。

等价于 pdf-cjk-font.md 第 5 节的手工步骤：
  1. 定位字体（默认 fc-match 宋体）并检查点阵表 (EBDT/EBLC ...)；
  2. 逐字面导出为不含点阵的 TTF，写到 --dest-dir；
  3. 刷新 fontconfig 缓存，按字体族找出同族里多余的那几份文件；
  4. 处理原 .ttc 与同族残留（默认删除；--mode move 则移到 --backup-dir）；
  5. 再刷新缓存、校验「一个族只剩新装的那份」，打印验证命令并提醒重启 Java。

为什么默认删除而不是改名成 xxx.bak：fontconfig 不按扩展名过滤，.bak 一样会被
当成字体加载，于是同一个族有两份文件，谁生效取决于目录/文件名排序，随时可能
翻转回点阵。要留原件请用 --mode move，并把 --backup-dir 放在 fontconfig 扫描
路径之外（如 /root/font-backup）。

用法:
    python3 fix-font-bitmaps.py --dry-run          # 只看计划，不动任何文件
    python3 fix-font-bitmaps.py                    # 按默认值执行（先打印计划并确认）
    python3 fix-font-bitmaps.py --yes              # 跳过确认（适合远程/脚本执行）
    python3 fix-font-bitmaps.py --mode move --backup-dir /root/font-backup
    python3 fix-font-bitmaps.py --source /usr/share/fonts/chinese/simsun.ttc \
        --dest-dir /usr/local/share/fonts/simsun

兼容 Python 3.6+，无第三方依赖；与 strip-font-bitmaps.py 放在同一目录即可。
"""
import argparse
import importlib.util
import os
import re
import shutil
import struct
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
FAMILY = '宋体'
DEFAULT_DEST = '/usr/local/share/fonts/simsun'
DEFAULT_BACKUP = '/root/font-backup'


def load_strip():
    """加载同目录下的 strip-font-bitmaps.py，复用其解析/剥离逻辑。"""
    path = os.path.join(HERE, 'strip-font-bitmaps.py')
    if not os.path.exists(path):
        sys.exit('[错误] 缺少 %s，请与 fix-font-bitmaps.py 放在同一目录' % path)
    spec = importlib.util.spec_from_file_location('strip_font_bitmaps', path)
    mod = importlib.util.module_from_spec(spec)
    saved = sys.dont_write_bytecode
    sys.dont_write_bytecode = True   # 不在 docs/tools 下留 __pycache__
    try:
        spec.loader.exec_module(mod)
    finally:
        sys.dont_write_bytecode = saved
    return mod


def run(cmd):
    """执行命令，返回 (returncode, stdout+stderr)；命令不存在时 returncode = -1。"""
    try:
        # 注意: capture_output/ text 是 Python 3.7+ 才有，这里用 3.6 也支持的写法
        p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                             universal_newlines=True)
    except OSError as e:
        return -1, str(e)
    out, _ = p.communicate()
    return p.returncode, out or ''


def default_source():
    """fc-match 解析族名，取第一个结果（一个 ttc 可能匹配到多个字面）。"""
    rc, out = run(['fc-match', '-f', '%{file}\n', FAMILY])
    if rc != 0:
        sys.exit('[错误] fc-match %s 失败: %s' % (FAMILY, out.strip()))
    paths = [line.strip() for line in out.splitlines() if line.strip()]
    if not paths:
        sys.exit('[错误] fc-match %s 没有返回字体文件' % FAMILY)
    return paths[0]


def face_count(path):
    """文件里的字面数量（ttc 看 ttcf 头，普通 ttf 为 1）。"""
    data = open(path, 'rb').read()
    if data[:4] == b'ttcf':
        return struct.unpack('>I', data[8:12])[0]
    return 1


def families_of(path, index):
    """用 fc-query 取该字面的族名列表（含本地化名，如 ['SimSun', '宋体']）。"""
    rc, out = run(['fc-query', '-f', '%{family}\n', '--index', str(index), path])
    if rc != 0:
        return []
    fams = []
    for line in out.splitlines():
        for item in line.split(','):
            item = item.strip()
            if item and item not in fams:
                fams.append(item)
    return fams


def pick_filename(families, index):
    """给字面挑一个文件名：优先纯 ASCII 的族名（SimSun -> SimSun.ttf）。"""
    name = None
    for fam in families:
        if all(ord(ch) < 128 for ch in fam):
            name = fam
            break
    if not name and families:
        name = families[0]
    if not name:
        name = 'face%d' % index
    clean = re.sub(r'[^A-Za-z0-9._-]+', '', name).strip('._-')
    return (clean or 'face%d' % index) + '.ttf'


def font_dirs():
    """fontconfig 的扫描目录列表（fc-cache -v 里以 Tab 缩进的那一段）。"""
    rc, out = run(['fc-cache', '-v'])
    dirs = []
    for line in out.splitlines():
        if line.startswith('\t'):
            item = line.strip()
            if item.startswith('/'):
                dirs.append(item)
    return dirs


def scanned_by(dest, dirs):
    """dest 是否落在某个扫描目录内（fontconfig 会递归扫描子目录）。"""
    target = os.path.realpath(dest)
    for root in dirs:
        real = os.path.realpath(root)
        if target == real or target.startswith(real.rstrip('/') + '/'):
            return root
    return None


def family_files(family):
    """当前 fontconfig 里属于该族的字体文件（去重排序）。"""
    rc, out = run(['fc-list', '-f', '%{file}\n', ':family=%s' % family])
    if rc != 0:
        return []
    return sorted({line.strip() for line in out.splitlines() if line.strip()})


def sibling_copies(src):
    """同目录下以原文件名为前缀的副本：simsun.ttc.bak / .simsun.ttc / simsun.ttc~ 等。"""
    directory = os.path.dirname(os.path.abspath(src))
    base = os.path.basename(src)
    try:
        names = sorted(os.listdir(directory))
    except OSError:
        return []
    return [os.path.join(directory, n) for n in names
            if n != base and n.lstrip('.').startswith(base)]


def unique_path(path):
    """路径已存在时追加 .1 .2 ... 直到不冲突（move 模式避免覆盖旧备份）。"""
    if not os.path.exists(path):
        return path
    n = 1
    while os.path.exists('%s.%d' % (path, n)):
        n += 1
    return '%s.%d' % (path, n)


def confirm(question):
    if not sys.stdin.isatty():
        sys.exit('非交互环境：确认计划无误后请加 --yes 重新执行')
    sys.stdout.write(question + ' [y/N] ')
    sys.stdout.flush()
    return sys.stdin.readline().strip().lower() in ('y', 'yes')


def main():
    ap = argparse.ArgumentParser(
        description='把带内嵌点阵的字体替换为纯矢量 TTF 并安装（详见 docs/pdf-cjk-font.md）')
    ap.add_argument('--source', metavar='FILE',
                    help='源字体文件（默认 fc-match %s 的第一个结果）' % FAMILY)
    ap.add_argument('--dest-dir', metavar='DIR', default=DEFAULT_DEST,
                    help='新 TTF 的安装目录（默认 %s）' % DEFAULT_DEST)
    ap.add_argument('--backup-dir', metavar='DIR', default=DEFAULT_BACKUP,
                    help='--mode move 时原字体的存放目录（默认 %s）' % DEFAULT_BACKUP)
    ap.add_argument('--mode', choices=('trash', 'move'), default='trash',
                    help='原字体处理方式：trash=删除（默认），move=移到 --backup-dir')
    ap.add_argument('--dry-run', action='store_true', help='只打印计划，不改动任何文件')
    ap.add_argument('--yes', action='store_true', help='跳过交互确认')
    ap.add_argument('--force', action='store_true', help='字体没有点阵表时也继续')
    args = ap.parse_args()

    dry = args.dry_run
    src = os.path.abspath(args.source or default_source())
    if not os.path.exists(src):
        sys.exit('[错误] 找不到源字体 %s' % src)
    try:
        total_faces = face_count(src)
    except (OSError, struct.error, IndexError):
        sys.exit('[错误] %s 不是可识别的 TTF/TTC 文件' % src)

    strip = load_strip()
    try:
        data = open(src, 'rb').read()
    except OSError as e:
        sys.exit('[错误] 无法读取 %s (%s)' % (src, e))
    offsets = strip.face_offsets(data)

    # ---- 1. 逐字面解析 + 去掉点阵 -----------------------------------------
    faces = []
    used, has_bitmap = {}, False
    for idx in range(min(total_faces, len(offsets))):
        blob, dropped, kept = strip.strip_face(data, offsets[idx])
        fams = families_of(src, idx)
        name = pick_filename(fams, idx)
        if name in used:
            used[name] += 1
            stem, ext = os.path.splitext(name)
            name = '%s%d%s' % (stem, used[name], ext)
        used[name] = 1
        has_bitmap = has_bitmap or bool(dropped)
        faces.append({'index': idx, 'families': fams, 'blob': blob,
                      'dropped': dropped, 'kept': kept,
                      'dest': os.path.join(args.dest_dir, name)})

    print('源字体: %s (%d 个字面)' % (src, len(faces)))
    for f in faces:
        print('  face[%d] %-24s 点阵表: %-20s 保留 %d 张 -> %s'
              % (f['index'], ','.join(f['families']) or '?',
                 str(f['dropped']) if f['dropped'] else '无', f['kept'], f['dest']))

    if not has_bitmap and not args.force:
        print('\n没有检测到内嵌点阵表，无需处理（要强制执行请加 --force）。')
        return 0

    # ---- 2. 校验目标目录在 fontconfig 扫描路径内 ---------------------------
    dirs = font_dirs()
    root = scanned_by(args.dest_dir, dirs)
    if root:
        print('\n目标目录 %s 在 fontconfig 扫描路径内（%s），安装后即可生效。'
              % (args.dest_dir, root))
    else:
        print('\n[警告] 目标目录 %s 不在 fontconfig 扫描路径内，装进去也不会被加载。'
              % args.dest_dir)
        print('       扫描目录: %s' % (', '.join(dirs) or '(未解析到)'))
        print('       请改用 /usr/local/share/fonts 下的子目录，或在 fonts.conf 里加 <dir>。')
        if not dry:
            sys.exit('[中止] 请先用 --dest-dir 指定一个会被扫描的目录')

    if args.mode == 'move':
        inside = scanned_by(args.backup_dir, dirs)
        if inside:
            sys.exit('[中止] --backup-dir %s 在扫描目录 %s 内，备份仍会被当成字体加载；'
                     '请换到扫描路径之外' % (args.backup_dir, inside))

    # ---- 3. 收集要被替换掉的旧文件 ----------------------------------------
    new_paths = {os.path.realpath(f['dest']) for f in faces}
    stale = []
    for f in faces:
        for fam in f['families']:
            for path in family_files(fam):
                if os.path.realpath(path) not in new_paths and path not in stale:
                    stale.append(path)
    for path in sibling_copies(src):
        if os.path.realpath(path) not in new_paths and path not in stale:
            stale.append(path)
    if src not in stale:
        stale.insert(0, src)

    verb = '删除' if args.mode == 'trash' else '移动到 %s' % args.backup_dir
    print('\n计划:')
    for f in faces:
        print('  写入 %s   (%s)' % (f['dest'], '去掉 ' + ','.join(f['dropped'])
                                    if f['dropped'] else '原样重建'))
    for path in stale:
        mark = '（本次源字体）' if path == src else ''
        print('  %s %s%s' % (verb, path, mark))
    print('  fc-cache -f   （两次：安装后、清理后各一次）')

    if dry:
        print('\n--dry-run: 未改动任何文件。')
        return 0

    if not args.yes and not confirm('\n确认执行?'):
        print('已取消。')
        return 1

    # ---- 4. 写入新 TTF ----------------------------------------------------
    try:
        if not os.path.isdir(args.dest_dir):
            os.makedirs(args.dest_dir)
        for f in faces:
            with open(f['dest'], 'wb') as fp:
                fp.write(f['blob'])
    except OSError as e:
        sys.exit('[错误] 写入 %s 失败: %s\n       安装字体通常需要 root 权限。'
                 % (args.dest_dir, e))
    for f in faces:
        print('已写入 %s' % f['dest'])

    run(['fc-cache', '-f'])

    # ---- 5. 处理旧文件 ----------------------------------------------------
    for path in sorted(set(stale)):
        try:
            if args.mode == 'trash':
                os.remove(path)
                print('已删除 %s' % path)
            else:
                if not os.path.isdir(args.backup_dir):
                    os.makedirs(args.backup_dir)
                target = unique_path(os.path.join(args.backup_dir,
                                                  os.path.basename(path)))
                shutil.move(path, target)
                print('已移动 %s -> %s' % (path, target))
        except OSError as e:
            print('[警告] 处理 %s 失败: %s' % (path, e))

    run(['fc-cache', '-f'])

    # ---- 6. 校验 ----------------------------------------------------------
    print('\n校验:')
    ok = True
    seen = set()
    for f in faces:
        for fam in f['families']:
            if fam in seen:
                continue
            seen.add(fam)
            paths = family_files(fam)
            if len(paths) == 1 and os.path.realpath(paths[0]) == os.path.realpath(f['dest']):
                print('  %-12s -> %s  （仅此一份）' % (fam, paths[0]))
            else:
                ok = False
                print('  %-12s -> 发现 %d 份: %s  [异常]' % (fam, len(paths), paths))

    print('')
    if ok:
        print('完成。字体轮廓不变，只是不再输出点阵字形。')
    else:
        print('[注意] 仍有同族的多份字体文件，谁生效取决于排序，请按上面的列表清理。')
    src_dir, src_base = os.path.dirname(src), os.path.basename(src)
    new_names = ' '.join(f['dest'] for f in faces)
    print('\n下一步:')
    print('  1) 重启 Java 应用（常驻 Chrome 进程启动时缓存字体列表，不重启不生效）；')
    print('     或等 PdfMakerService 的 idle 超时（默认 5 分钟）回收进程后重建。')
    print('  2) 重新生成一份 PDF 后验证:')
    print('       python3 pdf-glyph-report.py /tmp/after.pdf   # 应为 bitmap=0')
    print('       pdffonts /tmp/after.pdf | grep -i simsun')
    if args.mode == 'move':
        print('  3) 回滚: mv %s %s/ && rm %s && fc-cache -f'
              % (os.path.join(args.backup_dir, src_base), src_dir, new_names))
    else:
        print('  3) 回滚: 原件已按 --mode trash 删除。回滚需先取回 %s 放回 %s/，'
              % (src_base, src_dir))
        print('     再 rm %s && fc-cache -f（避免两份共存）；想留备份请用 --mode move。'
              % new_names)
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main())
