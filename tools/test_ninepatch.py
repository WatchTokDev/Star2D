#!/usr/bin/env python3
"""Cross-checks the 9-patch marker parser: builds sample .9.png files with Pillow, parses them with an independent Python
re-implementation AND with the real Java class (app/src/main/java/com/star4droid/template/Utils/NinePatchParser.java, compiled
with plain javac), and compares. Run: python3 tools/test_ninepatch.py"""
import os, subprocess, sys, tempfile
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PARSER = os.path.join(ROOT, "app/src/main/java/com/star4droid/template/Utils/NinePatchParser.java")
HARNESS = os.path.join(ROOT, "tools/ninepatch/ParseDump.java")

BLACK = (0, 0, 0, 255)

def is_marker(p):  # same rule as NinePatchParser.isMarker (p = (r,g,b,a))
    r, g, b, a = p
    return a >= 128 and r < 128 and g < 128 and b < 128

def py_parse(img):
    w, h = img.size
    if w < 3 or h < 3:
        return (False, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, False, False)
    px = img.load()
    cw, ch = w - 2, h - 2
    def span(coords):
        idx = [i for i, (x, y) in enumerate(coords) if is_marker(px[x, y])]
        return (idx[0], idx[-1]) if idx else None
    sx = span([(x, 0) for x in range(1, w - 1)])
    sy = span([(0, y) for y in range(1, h - 1)])
    pxs = span([(x, h - 1) for x in range(1, w - 1)])
    pys = span([(w - 1, y) for y in range(1, h - 1)])
    left, right = (sx[0], cw - 1 - sx[1]) if sx else (0, 0)
    top, bottom = (sy[0], ch - 1 - sy[1]) if sy else (0, 0)
    pl, pr = (pxs[0], cw - 1 - pxs[1]) if pxs else (0, 0)
    pt, pb = (pys[0], ch - 1 - pys[1]) if pys else (0, 0)
    return (True, cw, ch, left, right, top, bottom, pl, pr, pt, pb, sx is not None, sy is not None)

def blank(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))

def fill_picture(img):
    w, h = img.size
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            img.putpixel((x, y), (40 + x % 200, 120, 200, 255))

def case_button():
    # 20x14 picture (22x16 file): 6px fixed corners, stretch x 6..13, y 5..8, padding 3 left/right, 2 top, 4 bottom
    im = blank(22, 16); fill_picture(im)
    for x in range(1 + 6, 1 + 14): im.putpixel((x, 0), BLACK)
    for y in range(1 + 5, 1 + 9): im.putpixel((0, y), BLACK)
    for x in range(1 + 3, 1 + 17): im.putpixel((x, 15), BLACK)
    for y in range(1 + 2, 1 + 10): im.putpixel((21, y), BLACK)
    return "button", im

def case_multi_span():
    im = blank(32, 12); fill_picture(im)
    for x in (1 + 4, 1 + 5, 1 + 20, 1 + 21): im.putpixel((x, 0), BLACK)   # two spans -> outer bounds 4..21
    for y in (1 + 2, 1 + 7): im.putpixel((0, y), BLACK)                  # two single rows -> 2..7
    return "multi_span", im

def case_no_markers():
    im = blank(12, 12); fill_picture(im)
    return "no_markers", im

def case_corners_ignored():
    im = blank(10, 10); fill_picture(im)
    for c in ((0, 0), (9, 0), (0, 9), (9, 9)): im.putpixel(c, BLACK)
    im.putpixel((1 + 3, 0), BLACK); im.putpixel((0, 1 + 3), BLACK)
    return "corners_ignored", im

def case_tolerance():
    im = blank(14, 14); fill_picture(im)
    for x in range(1 + 4, 1 + 8): im.putpixel((x, 0), (20, 20, 20, 200))   # near-black, semi opaque: marker
    for y in range(1 + 4, 1 + 8): im.putpixel((0, y), (255, 255, 255, 255)) # white: NOT a marker
    for x in range(1 + 4, 1 + 8): im.putpixel((x, 13), (0, 0, 0, 100))      # transparent-ish black: NOT a marker
    return "tolerance", im

def case_edge_stretch():
    im = blank(8, 8); fill_picture(im)
    for x in range(1, 7): im.putpixel((x, 0), BLACK)       # whole width stretches -> left=right=0
    im.putpixel((0, 1), BLACK)                              # first row only -> top=0, bottom=5
    return "edge_stretch", im

def case_too_small():
    return "too_small", blank(2, 2)

def java_parse(img, tmp):
    w, h = img.size
    px = img.load()
    dump = os.path.join(tmp, "dump.txt")
    with open(dump, "w") as f:
        f.write("%d %d\n" % (w, h))
        for y in range(h):
            f.write(" ".join(str((r << 24) | (g << 16) | (b << 8) | a) for (r, g, b, a) in (px[x, y] for x in range(w))) + "\n")
    out = subprocess.check_output(["java", "-cp", os.path.join(tmp, "cls"), "ParseDump", dump], text=True).split()
    return (out[0] == "true",) + tuple(int(v) for v in out[1:11]) + (out[11] == "true", out[12] == "true")

def main():
    tmp = tempfile.mkdtemp(prefix="np_")
    os.makedirs(os.path.join(tmp, "cls"))
    subprocess.check_call(["javac", "-d", os.path.join(tmp, "cls"), PARSER, HARNESS])
    bad = 0
    for mk in (case_button, case_multi_span, case_no_markers, case_corners_ignored, case_tolerance, case_edge_stretch, case_too_small):
        name, im = mk()
        im.save(os.path.join(tmp, name + ".9.png"))
        py, jv = py_parse(im), java_parse(im, tmp)
        ok = py == jv
        bad += not ok
        print(("OK   " if ok else "FAIL ") + name, "valid=%s content=%sx%s splits(l,r,t,b)=%s pad(l,r,t,b)=%s" % (jv[0], jv[1], jv[2], jv[3:7], jv[7:11]))
        if not ok: print("   python:", py, "\n   java:  ", jv)
    # explicit expectations for the main case (hand-computed from the markers drawn above)
    exp = (True, 20, 14, 6, 6, 5, 5, 3, 3, 2, 4, True, True)
    got = java_parse(case_button()[1], tmp)
    if got != exp:
        bad += 1
        print("FAIL button expectation", got, "!=", exp)
    else:
        print("OK   button matches the hand-computed splits/padding")
    print("ALL OK" if not bad else "%d FAILED" % bad)
    return 1 if bad else 0

if __name__ == "__main__":
    sys.exit(main())
