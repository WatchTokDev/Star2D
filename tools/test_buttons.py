#!/usr/bin/env python3
"""
Offline checks for the Button Maker (the Android renderer itself cannot run here).

It does NOT re-implement the drawing. It ports the three pieces of ARITHMETIC that must be exactly right and checks them
independently of the Java code:

 1. NinePatchWriter  : border markers (top/left = stretch, bottom/right = padding), compared with a literal port of the runtime
                       reader app/src/main/java/com/star4droid/template/Utils/NinePatchParser.java.
 2. PngMeta          : the tEXt chunk inserted after IHDR (CRC, ordering) must keep the PNG valid for Pillow and be readable back.
 3. ButtonRenderer geometry : margins for shadow/glow, body rectangle, 9-patch stretch rows/columns and content box for every
                       preset and shape, checking the invariants a 9-patch needs (non-empty stretch span, caps inside the image...).

Run:  python3 tools/test_buttons.py
"""
import io
import math
import struct
import sys
import zlib

from PIL import Image

FAILS = []


def check(cond, msg):
    if not cond:
        FAILS.append(msg)
        print("FAIL", msg)


# ---------------------------------------------------------------------------------------------- 1. 9-patch markers
BLACK = (0, 0, 0, 255)


def write_ninepatch(img, sx0, sx1, sy0, sy1, cx0, cx1, cy0, cy1):
    """Port of NinePatchWriter.toNinePatch (picture pixel (x, y) lives at (x + 1, y + 1))."""
    w, h = img.size
    out = Image.new("RGBA", (w + 2, h + 2), (0, 0, 0, 0))
    out.paste(img, (1, 1))
    px = out.load()
    for x in range(sx0, sx1):
        px[x + 1, 0] = BLACK
    for y in range(sy0, sy1):
        px[0, y + 1] = BLACK
    for x in range(cx0, cx1):
        px[x + 1, h + 1] = BLACK
    for y in range(cy0, cy1):
        px[w + 1, y + 1] = BLACK
    return out


def is_marker(rgba):
    r, g, b, a = rgba
    return a >= 128 and r < 128 and g < 128 and b < 128


def parse_ninepatch(im):
    """Literal port of NinePatchParser.parse (what the game runtime and the editor use)."""
    w, h = im.size
    px = im.load()
    cw, ch = w - 2, h - 2
    sx1 = sx2 = -1
    for x in range(1, w - 1):
        if is_marker(px[x, 0]):
            if sx1 < 0:
                sx1 = x - 1
            sx2 = x - 1
    sy1 = sy2 = -1
    for y in range(1, h - 1):
        if is_marker(px[0, y]):
            if sy1 < 0:
                sy1 = y - 1
            sy2 = y - 1
    res = dict(cw=cw, ch=ch, left=sx1, right=cw - 1 - sx2, top=sy1, bottom=ch - 1 - sy2, padL=0, padR=0, padT=0, padB=0)
    p1 = p2 = -1
    for x in range(1, w - 1):
        if is_marker(px[x, h - 1]):
            if p1 < 0:
                p1 = x - 1
            p2 = x - 1
    if p1 >= 0:
        res["padL"], res["padR"] = p1, cw - 1 - p2
    q1 = q2 = -1
    for y in range(1, h - 1):
        if is_marker(px[w - 1, y]):
            if q1 < 0:
                q1 = y - 1
            q2 = y - 1
    if q1 >= 0:
        res["padT"], res["padB"] = q1, ch - 1 - q2
    return res


def test_markers():
    for (W, H, sx0, sx1, sy0, sy1, cx0, cx1, cy0, cy1) in [
        (128, 64, 30, 98, 25, 31, 12, 116, 8, 50),
        (96, 96, 20, 76, 20, 76, 10, 86, 10, 80),
        (40, 24, 1, 39, 1, 23, 2, 38, 2, 20),
        (512, 512, 255, 257, 255, 257, 0, 512, 0, 512),
    ]:
        img = Image.new("RGBA", (W, H), (200, 50, 50, 255))
        np_ = write_ninepatch(img, sx0, sx1, sy0, sy1, cx0, cx1, cy0, cy1)
        # through a real PNG round trip
        bio = io.BytesIO()
        np_.save(bio, "PNG")
        back = Image.open(io.BytesIO(bio.getvalue())).convert("RGBA")
        r = parse_ninepatch(back)
        tag = f"{W}x{H}"
        check(back.size == (W + 2, H + 2), f"{tag}: file size is picture + 2")
        check(r["left"] == sx0 and r["right"] == W - sx1, f"{tag}: stretch x = caps ({r['left']},{r['right']}) expected ({sx0},{W - sx1})")
        check(r["top"] == sy0 and r["bottom"] == H - sy1, f"{tag}: stretch y = caps ({r['top']},{r['bottom']}) expected ({sy0},{H - sy1})")
        check(r["padL"] == cx0 and r["padR"] == W - cx1, f"{tag}: padding x ({r['padL']},{r['padR']}) expected ({cx0},{W - cx1})")
        check(r["padT"] == cy0 and r["padB"] == H - cy1, f"{tag}: padding y ({r['padT']},{r['padB']}) expected ({cy0},{H - cy1})")
        px = back.load()
        for c in [(0, 0), (W + 1, 0), (0, H + 1), (W + 1, H + 1)]:
            check(px[c][3] == 0, f"{tag}: border corner {c} must be transparent")
        # the picture itself must be untouched
        check(back.crop((1, 1, W + 1, H + 1)).tobytes() == img.tobytes(), f"{tag}: picture pixels unchanged")


# ---------------------------------------------------------------------------------------------- 2. tEXt chunk
SIG = b"\x89PNG\r\n\x1a\n"


def with_text(png, key, text):
    """Port of PngMeta.withText."""
    assert png[:8] == SIG
    out = bytearray(png[:8])
    pos = 8
    inserted = False
    while pos + 12 <= len(png):
        ln = struct.unpack(">I", png[pos:pos + 4])[0]
        typ = png[pos + 4:pos + 8].decode("latin-1")
        total = 12 + ln
        ours = typ == "tEXt" and png[pos + 8:pos + 8 + ln].split(b"\0", 1)[0].decode("latin-1") == key
        if not ours:
            out += png[pos:pos + total]
        if not inserted and typ == "IHDR":
            data = key.encode("latin-1") + b"\0" + text.encode("latin-1")
            crc = zlib.crc32(b"tEXt" + data) & 0xFFFFFFFF
            out += struct.pack(">I", len(data)) + b"tEXt" + data + struct.pack(">I", crc)
            inserted = True
        pos += total
        if typ == "IEND":
            break
    return bytes(out)


def read_text_prefix(png, key):
    """Port of PngMeta.readText(File): reads only the first 4 KB, stops at IDAT."""
    png = png[:4096]
    pos = 8
    while pos + 12 <= len(png):
        ln = struct.unpack(">I", png[pos:pos + 4])[0]
        typ = png[pos + 4:pos + 8].decode("latin-1")
        if typ == "IDAT":
            return None
        if pos + 12 + ln > len(png):
            return None
        if typ == "tEXt":
            k, _, v = png[pos + 8:pos + 8 + ln].partition(b"\0")
            if k.decode("latin-1") == key:
                return v.decode("latin-1")
        pos += 12 + ln
    return None


def test_png_meta():
    img = Image.new("RGBA", (130, 66), (10, 200, 30, 255))
    bio = io.BytesIO()
    img.save(bio, "PNG")
    plain = bio.getvalue()
    spec = '{"preset":"jelly","shape":"rounded","colors":["#5DAEFF","#2D8CFF"],"width":128}'
    withm = with_text(plain, "star2d.button", spec)
    back = Image.open(io.BytesIO(withm))
    back.load()  # decodes fully: CRCs and chunk order are valid
    check(back.tobytes() == img.tobytes(), "png with tEXt decodes to the same pixels")
    check(back.text.get("star2d.button") == spec, "Pillow reads the stored settings back")
    check(read_text_prefix(withm, "star2d.button") == spec, "prefix reader (first 4 KB) finds the chunk")
    # inserting twice replaces, never duplicates
    again = with_text(withm, "star2d.button", spec.replace("jelly", "candy"))
    check(again.count(b"star2d.button") == 1, "second write replaces the first chunk")
    check(Image.open(io.BytesIO(again)).text["star2d.button"].find("candy") > 0, "replaced value is the new one")
    # a 9-patch with metadata is still a valid 9-patch
    np_ = write_ninepatch(img, 30, 100, 20, 40, 10, 120, 8, 58)
    b2 = io.BytesIO()
    np_.save(b2, "PNG")
    meta9 = with_text(b2.getvalue(), "star2d.button", spec)
    r = parse_ninepatch(Image.open(io.BytesIO(meta9)).convert("RGBA"))
    check(r["left"] == 30 and r["bottom"] == 66 - 40, "9-patch + tEXt still parses")


# ---------------------------------------------------------------------------------------------- 3. geometry
# Mirror of ButtonRenderer.geometry / capExtent / stretch + content computation.
# Preset numbers copied from ButtonPresets: (id, shape, w, h, radiusRatio, chamferRatio, shadowBlur, shadowDy, shadowDx, glowBlur, lip, ringsTotal)
PRESETS = [
    ("jelly", "rounded", 128, 64, 0.32, 0.45, 3, 3, 0, 0, 0),
    ("candy", "rounded", 128, 56, 0.5, 0.45, 2, 2, 0, 0, 0),
    ("flat_3d", "rounded", 128, 64, 0.36, 0.45, 0, 0, 0, 0, 6),
    ("glass_neon", "rounded", 128, 56, 0.16, 0.45, 3, 2, 0, 5, 0),
    ("hex_arcade", "chamfer", 160, 64, 0.3, 0.46, 3, 3, 0, 0, 0),
    ("flat", "rounded", 128, 56, 0.22, 0.45, 2, 2, 0, 0, 0),
    ("ghost", "rounded", 128, 56, 0.22, 0.45, 0, 0, 0, 0, 0),
    ("panel_dark", "rounded", 192, 128, 0.12, 0.45, 6, 4, 0, 0, 0),
    ("panel_light", "rounded", 192, 128, 0.12, 0.45, 5, 3, 0, 0, 0),
    ("jelly_pill", "pill", 160, 56, 0.5, 0.45, 3, 3, 0, 0, 0),
    ("jelly_square", "rounded", 96, 96, 0.32, 0.45, 3, 3, 0, 0, 0),
    ("jelly_circle", "circle", 96, 96, 0.5, 0.45, 3, 3, 0, 0, 0),
    ("jelly_blob", "blob", 96, 96, 0.14, 0.45, 3, 3, 0, 0, 0),
    ("jelly_arrow_left", "triangle_left", 96, 96, 0.14, 0.45, 3, 3, 0, 0, 0),
    ("jelly_arrow_right", "triangle_right", 96, 96, 0.14, 0.45, 3, 3, 0, 0, 0),
]
STRETCH = ("rect", "rounded", "pill", "chamfer")


def geometry(shape, W, H, rr, cr, sb, sdy, sdx, gb, lip, drop=0.0):
    k = 1.25
    padL = padR = max(1, math.ceil(max(sb * k + abs(sdx), gb * k)))
    padT = max(1, math.ceil(max(sb * k - sdy, gb * k)))
    padB = max(1, math.ceil(max(sb * k + sdy, gb * k)))
    mx, my = max(1, int(W * 0.3)), max(1, int(H * 0.3))
    padL, padR, padT, padB = min(padL, mx), min(padR, mx), min(padT, my), min(padB, my)
    body = (padL, padT + drop, W - padR, H - padB - lip)
    bw, bh = body[2] - body[0], body[3] - body[1]
    mn = max(1.0, min(bw, bh))
    if shape == "rect":
        rad = 0.0
    elif shape == "pill":
        rad = mn / 2
    elif shape == "circle":
        rad = mn / 2
    else:
        rad = rr * mn
    rad = max(0.0, min(rad, mn / 2))
    cham = max(0.0, min(cr * bh, mn / 2))
    return body, rad, cham, (padL, padR, padT, padB)


def stretch_and_content(shape, W, H, body, rad, cham):
    e = 0.0 if shape == "rect" else cham if shape == "chamfer" else rad
    sx0, sx1 = math.ceil(body[0] + e), math.floor(body[2] - e)
    sy0, sy1 = math.ceil(body[1] + e), math.floor(body[3] - e)
    if sx1 - sx0 < 2:
        mid = round((body[0] + body[2]) / 2)
        sx0, sx1 = mid - 1, mid + 1
    if sy1 - sy0 < 2:
        mid = round((body[1] + body[3]) / 2)
        sy0, sy1 = mid - 1, mid + 1
    sx0 = max(0, min(sx0, W - 2))
    sx1 = max(sx0 + 1, min(sx1, W - 1))
    sy0 = max(0, min(sy0, H - 2))
    sy1 = max(sy0 + 1, min(sy1, H - 1))
    return sx0, sx1, sy0, sy1


def test_geometry():
    for (pid, shape, W, H, rr, cr, sb, sdy, sdx, gb, lip) in PRESETS:
        for drop_lip in (0, 1):  # normal and pressed look (pressed moves the body down by the lip)
            drop = lip * 0.85 if drop_lip else 0.0
            plip = lip * 0.15 if drop_lip else lip
            body, rad, cham, pads = geometry(shape, W, H, rr, cr, sb, sdy, sdx, gb, plip, drop)
            tag = f"{pid}{' pressed' if drop_lip else ''}"
            check(body[2] - body[0] > 8 and body[3] - body[1] > 8, f"{tag}: body keeps a usable size {body}")
            check(all(p >= 1 for p in pads), f"{tag}: every margin >= 1 px (anti-aliased edge stays transparent)")
            check(body[0] >= 1 and body[1] >= 1 and body[2] <= W - 1 and body[3] + plip <= H - 1 + 1e-6, f"{tag}: body + lip inside the bitmap")
            if shape in STRETCH:
                sx0, sx1, sy0, sy1 = stretch_and_content(shape, W, H, body, rad, cham)
                check(sx1 > sx0 and sy1 > sy0, f"{tag}: stretch span not empty")
                check(0 <= sx0 < sx1 <= W - 1 and 0 <= sy0 < sy1 <= H - 1, f"{tag}: stretch inside the picture ({sx0},{sx1},{sy0},{sy1})")
                # margins (shadow) must stay in the fixed caps: the stretchable span starts after the body's left/top edge
                check(sx0 >= pads[0] and sy0 >= pads[2], f"{tag}: left/top shadow margin is in a fixed cap")
                check(W - sx1 >= pads[1] and H - sy1 >= pads[3] + plip - 1, f"{tag}: right/bottom margin (+lip) is in a fixed cap")
                minw, minh = sx0 + (W - sx1), sy0 + (H - sy1)
                check(minw < W and minh < H, f"{tag}: min size {minw}x{minh} below base {W}x{H}")
                # a 9-patch built from these numbers must parse back to the same caps
                img = Image.new("RGBA", (W, H), (255, 255, 255, 255))
                np_ = write_ninepatch(img, sx0, sx1, sy0, sy1, sx0, sx1, sy0, sy1)
                r = parse_ninepatch(np_)
                check(r["left"] == sx0 and r["right"] == W - sx1 and r["top"] == sy0 and r["bottom"] == H - sy1, f"{tag}: caps round trip")
            else:
                check(W == H, f"{tag}: fixed shapes are square (saved as plain PNG, not a 9-patch)")


def main():
    test_markers()
    test_png_meta()
    test_geometry()
    if FAILS:
        print(f"\n{len(FAILS)} check(s) FAILED")
        return 1
    print("ALL OK: 9-patch markers == runtime parser, tEXt chunk valid, geometry invariants hold for", len(PRESETS), "presets (normal + pressed)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
