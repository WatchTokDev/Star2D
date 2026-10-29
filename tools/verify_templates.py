#!/usr/bin/env python3
"""Offline verification of the game templates (remote/Template*.java).

What it does (no device, no Android build needed):
  1. compiles the PURE-Java template generators with plain javac against Gson, together with the REAL BlockCatalog / BlockModel /
     BlockBuilder of the app (Android pieces replaced by tiny stubs in tools/templatecheck/stubs);
  2. builds every template for portrait 720x1280 and landscape 1280x720 (default params + extreme samples) and checks the tool calls
     against the real block catalog (titles, field names, reporter argument counts), the element property tables, z policy, layout,
     text metrics from default.fnt, jump reachability ...        (Check.java)
  3. turns every event into Java with BlockModel.exportCode() and COMPILES it inside a scene class against the app's compiled engine
     classes (needs a previous Gradle compile: app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes);
  4. renders a preview PNG of every scene (UI layer and the start of the world) into the output folder.

NOT covered (needs the phone): the real engine running the scenes - physics feel, touch handling, text rendering, sounds.

usage: python3 tools/verify_templates.py [outdir]      (exit code 1 on any failure)
"""
import glob
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/tc_templates"
SRC = os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d")
ASSETS = os.path.join(ROOT, "app/src/main/assets")
CHK = os.path.join(ROOT, "tools/templatecheck")
CLS = os.path.join(OUT, "classes")
ENGINE = os.path.join(ROOT, "app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes")


def find_jar(pattern):
    hits = glob.glob(os.path.expanduser("~/.gradle/caches/modules-2/files-2.1/" + pattern), recursive=True)
    return sorted(hits)[-1] if hits else None


GSON = find_jar("com.google.code.gson/gson/*/*/gson-*.jar")
GDX = find_jar("com.badlogicgames.gdx/gdx/1.14.2/*/gdx-1.14.2.jar")
BOX2D = find_jar("com.badlogicgames.gdx/gdx-box2d/1.14.2/*/gdx-box2d-1.14.2.jar")
LIGHTS = find_jar("**/box2dlights*.jar")
ANDROID = "/opt/android-sdk/platforms/android-36/android.jar"


def run(cmd, **kw):
    env = dict(os.environ)
    env["JAVA_TOOL_OPTIONS"] = ""
    return subprocess.run(cmd, capture_output=True, text=True, env=env, **kw)


def step(msg):
    print("\n### " + msg, flush=True)


def main():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(CLS, exist_ok=True)
    failures = 0

    step("1. compile templates + real block catalog")
    srcs = glob.glob(os.path.join(SRC, "remote/Template*.java")) + glob.glob(os.path.join(CHK, "stubs/**/*.java"), recursive=True)
    srcs += [os.path.join(SRC, "ui/blocks/BlockCatalog.java"), os.path.join(SRC, "ui/blocks/BlockModel.java"), os.path.join(SRC, "remote/BlockBuilder.java"),
             os.path.join(CHK, "src/com/star4droid/star2d/remote/Check.java")]
    # Templates.java (the Android side) is not part of the offline build
    srcs = [s for s in srcs if not s.endswith("/Templates.java")]
    r = run(["javac", "-nowarn", "-d", CLS, "-cp", GSON] + srcs)
    if r.returncode != 0:
        print(r.stdout + r.stderr)
        print("COMPILE FAILED")
        return 1
    print("ok (%d files)" % len(srcs))

    step("2. build + check every template")
    r = run(["java", "-cp", CLS + ":" + GSON, "com.star4droid.star2d.remote.Check", ASSETS, OUT])
    print(r.stdout + r.stderr)
    if r.returncode != 0:
        failures += 1

    step("3. compile the generated Java against the engine")
    failures += compile_events()

    step("4. previews")
    try:
        previews()
    except Exception as e:  # previews are a convenience, never a reason to fail
        print("preview skipped:", e)

    print("\nRESULT:", "FAILED" if failures else "ALL CHECKS PASSED", "(output in %s)" % OUT)
    return 1 if failures else 0


# ---------------------------------------------------------------------------------------------------------------------------------
# generated Java -> compile

WRAP_HEAD = """package com.star4droid.Game;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Timer;
import com.star4droid.template.Items.*;
import com.star4droid.template.Utils.PlayerItem;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
"""


def indent(code):
    return "\n".join("        " + l for l in code.split("\n"))


def wrapper(tag):
    d = json.load(open(os.path.join(OUT, tag + ".json")))
    code = json.load(open(os.path.join(OUT, tag + ".code.json")))
    fields, names = [], []
    variables = []
    for st in d["steps"]:
        if st["tool"] == "variables":
            for v in st["args"]["ops"]:
                variables.append("    %s %s = %s;" % (v["type"], v["name"], v["value"]))
        if st["tool"] == "elements":
            for op in st["args"]["ops"]:
                names.append(op["name"])
    for n in names:
        fields.append("    PlayerItem %s;" % n)
    cls = "T_" + re.sub(r"\W", "_", tag)
    out = [WRAP_HEAD, "public class %s extends StageImp {" % cls]
    out += variables + fields
    scene = {"OnCreate": "onCreate", "OnStep": "onDraw", "OnPause": "onPause", "OnResume": "onResume"}
    have = set()
    for key, java in code.items():
        if key.startswith("scene."):
            ev = key[6:]
            if ev in scene:
                out.append("    @Override public void %s(){\n%s\n    }" % (scene[ev], indent(java)))
                have.add(ev)
            elif ev == "onCollisionStart":
                out.append("    @Override public void onCollisionBegin(PlayerItem body1,PlayerItem body2){\n%s\n    }" % indent(java))
                have.add(ev)
            elif ev == "onCollisionEnd":
                out.append("    @Override public void onCollisionEnd(PlayerItem body1,PlayerItem body2){\n%s\n    }" % indent(java))
                have.add(ev)
    for ev, m in (("OnCreate", "onCreate"), ("OnPause", "onPause"), ("OnResume", "onResume"), ("OnStep", "onDraw")):
        if ev not in have:
            out.append("    @Override public void %s(){}" % m)
    if "onCollisionStart" not in have:
        out.append("    @Override public void onCollisionBegin(PlayerItem body1,PlayerItem body2){}")
    if "onCollisionEnd" not in have:
        out.append("    @Override public void onCollisionEnd(PlayerItem body1,PlayerItem body2){}")
    sig = {"onClick": "PlayerItem current", "onTouchStart": "PlayerItem current, InputEvent event", "onTouchEnd": "PlayerItem current, InputEvent event",
           "OnBodyCreated": "PlayerItem current", "OnBodyUpdate": "PlayerItem current", "onBodyCollided": "PlayerItem current, PlayerItem body2",
           "onBodyCollideEnd": "PlayerItem current, PlayerItem body2"}
    for key, java in code.items():
        if key.startswith("scene."):
            continue
        el, ev = key.split(".", 1)
        out.append("    void ev_%s_%s(%s){\n%s\n    }" % (el, ev, sig[ev], indent(java)))
    out.append("}")
    return cls, "\n".join(out)


def compile_events():
    if not os.path.isdir(ENGINE):
        print("SKIPPED: no compiled engine classes at %s (run `bash gradlew :app:compileDebugJavaWithJavac` once)" % ENGINE)
        return 0
    wd = os.path.join(OUT, "gen")
    os.makedirs(wd, exist_ok=True)
    for f in glob.glob(wd + "/*.java"):
        os.remove(f)
    tags = open(os.path.join(OUT, "index.txt")).read().split()
    for t in tags:
        cls, src = wrapper(t)
        open(os.path.join(wd, cls + ".java"), "w").write(src)
    cp = ":".join(x for x in [ENGINE, GDX, BOX2D, LIGHTS, ANDROID, GSON] if x)
    cdir = os.path.join(OUT, "genclasses")
    os.makedirs(cdir, exist_ok=True)
    r = run(["javac", "-nowarn", "-proc:none", "-d", cdir, "-cp", cp] + glob.glob(wd + "/*.java"))
    txt = (r.stdout + r.stderr)
    if r.returncode != 0:
        print(txt[:6000])
        print("GENERATED JAVA DOES NOT COMPILE")
        return 1
    print("ok: %d generated scene classes compile" % len(tags))
    return 0


# ---------------------------------------------------------------------------------------------------------------------------------
# previews

def previews():
    from PIL import Image, ImageDraw
    import colorsys
    sys.path.insert(0, os.path.join(ROOT, "tools"))
    tags = open(os.path.join(OUT, "index.txt")).read().split()
    fnt = {}
    for line in open(os.path.join(ASSETS, "files/default.fnt")):
        m = re.match(r"char id=(\d+).*?xadvance=(-?\d+)", line)
        if m:
            fnt[int(m.group(1))] = int(m.group(2))

    def col(s, default=(255, 255, 255, 255)):
        s = s.lstrip("#")
        if len(s) == 6:
            return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
        if len(s) == 8:
            return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4, 6))
        return default

    def argb(s):
        s = s.lstrip("#")
        if len(s) == 8:
            return tuple(int(s[i:i + 2], 16) for i in (2, 4, 6, 0))
        return col("#" + s)

    def lerp(a, b, t):
        return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))

    def make_image(spec):
        kind = spec["kind"]
        if kind in ("pixels", "tiles"):
            rows = spec["rows"]
            pal = {k: argb(v) for k, v in spec["palette"].items()}
            pal["."] = (0, 0, 0, 0)
            pal[" "] = (0, 0, 0, 0)
            w, h = max(len(r) for r in rows), len(rows)
            im = Image.new("RGBA", (w, h))
            for y, r in enumerate(rows):
                for x, c in enumerate(r):
                    im.putpixel((x, y), pal[c])
            sc = spec.get("scale", 1)
            im = im.resize((w * sc, h * sc), Image.NEAREST)
            if kind == "tiles":
                out = Image.new("RGBA", (spec["width"], spec["height"]))
                for yy in range(0, spec["height"], im.height):
                    for xx in range(0, spec["width"], im.width):
                        out.paste(im, (xx, yy))
                return out
            return im
        w, h = spec["width"], spec["height"]
        im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        dr = ImageDraw.Draw(im)
        if kind == "gradient":
            cs = [argb(c) for c in spec["colors"]]
            for y in range(h):
                t = y / max(1, h - 1) * (len(cs) - 1)
                i = min(int(t), len(cs) - 2)
                dr.line([(0, y), (w, y)], fill=lerp(cs[i], cs[i + 1], t - i))
            return im
        fill = spec.get("fill", "#FFFFFF")
        base = Image.new("RGBA", (w, h), (255, 255, 255, 255))
        if fill.startswith("linear:") or fill.startswith("radial:"):
            parts = [p for p in fill[7:].split(",") if not re.fullmatch(r"-?[0-9.]+", p.strip())]
            cs = [argb(c.strip()) for c in parts]
            if fill.startswith("linear:"):
                d = ImageDraw.Draw(base)
                nums = [q.strip() for q in fill[7:].split(",") if re.fullmatch(r"-?[0-9.]+", q.strip())]
                horizontal = bool(nums) and float(nums[-1]) in (0.0, 180.0)
                span = w if horizontal else h
                for k in range(span):
                    t = k / max(1, span - 1) * (len(cs) - 1)
                    i = min(int(t), len(cs) - 2)
                    c = lerp(cs[i], cs[i + 1], t - i)
                    d.line([(k, 0), (k, h)] if horizontal else [(0, k), (w, k)], fill=c)
            else:
                px = base.load()
                for y in range(h):
                    for x in range(w):
                        t = min(1.0, (((x - w / 2) ** 2 + (y - h / 2) ** 2) ** 0.5) / (max(w, h) / 2))
                        px[x, y] = lerp(cs[0], cs[-1], t)
        else:
            base = Image.new("RGBA", (w, h), argb(fill))
        mask = Image.new("L", (w, h), 0)
        md = ImageDraw.Draw(mask)
        shp = spec.get("shape", "rect")
        sw = spec.get("strokeWidth", 2 if "stroke" in spec else 0)
        box = [sw / 2, sw / 2, w - sw / 2 - 1, h - sw / 2 - 1]
        if shp == "rect":
            md.rectangle(box, fill=255)
        elif shp == "rounded":
            md.rounded_rectangle(box, radius=spec.get("radius", min(w, h) / 5), fill=255)
        elif shp in ("circle", "ellipse"):
            md.ellipse(box, fill=255)
        elif shp == "triangle":
            md.polygon([(w / 2, box[1]), (box[2], box[3]), (box[0], box[3])], fill=255)
        elif shp == "star":
            import math
            pts = []
            for i in range(10):
                a = -math.pi / 2 + i * math.pi / 5
                rr = min(w, h) / 2 * (1 if i % 2 == 0 else 0.42)
                pts.append((w / 2 + math.cos(a) * rr, h / 2 + math.sin(a) * rr))
            md.polygon(pts, fill=255)
        im.paste(base, (0, 0), mask)
        if "stroke" in spec and sw > 0:
            sc = argb(spec["stroke"])
            d = ImageDraw.Draw(im)
            if shp == "rect":
                d.rectangle(box, outline=sc, width=int(sw))
            elif shp == "rounded":
                d.rounded_rectangle(box, radius=spec.get("radius", min(w, h) / 5), outline=sc, width=int(sw))
            elif shp in ("circle", "ellipse"):
                d.ellipse(box, outline=sc, width=int(sw))
            elif shp == "star":
                d.polygon(pts, outline=sc)
        return im

    for tag in tags:
        d = json.load(open(os.path.join(OUT, tag + ".json")))
        W, H = d["w"], d["h"]
        images = {}
        elements = []
        for st in d["steps"]:
            if st["tool"] == "create_image":
                images["/" + st["args"]["path"].split("/", 1)[1]] = make_image(st["args"])
            if st["tool"] == "elements":
                for op in st["args"]["ops"]:
                    elements.append(op)
        # world extent: scroll levels are drawn from x=0 over the frame width (the start of the level); a second strip shows the rest
        extra = max([op["props"].get("x", 0) + op["props"].get("width", 0) for op in elements if op["props"].get("type") != "UI" and op["type"] != "TEXT"] + [W])
        scroll = d.get("meta", {}).get("scrolls", False)
        strips = [0] if not scroll else list(range(0, int(extra), W))[:6]
        sheet = Image.new("RGBA", (W, H * len(strips) if False else H), (0, 0, 0, 255))
        pages = []
        for off in strips:
            canvas = Image.new("RGBA", (W, H), col(d.get("sceneColor", "#000000")))
            order = sorted(range(len(elements)), key=lambda i: (elements[i]["props"].get("z", 0), i))
            for i in order:
                op = elements[i]
                pr, ty = op["props"], op["type"]
                if pr.get("Visible") == "false":
                    continue
                ui = pr.get("type") == "UI" or ty in ("TEXT", "PROGRESS", "JOYSTICK") and pr.get("type", "UI") == "UI"
                x, y = pr.get("x", 0), pr.get("y", 0)
                if ty == "CIRCLE":
                    w = h2 = pr.get("radius", 10) * 2
                else:
                    w, h2 = pr.get("width", 50), pr.get("height", 50)
                if not ui:
                    x -= off
                if x + w < 0 or x > W:
                    continue
                tint = col(pr.get("Tint", "#FFFFFF"))
                if ty == "TEXT":
                    txt, sc = pr.get("Text", ""), pr.get("Font Scale", 1)
                    tw = sum(fnt.get(ord(c), 10) for c in txt) * sc
                    th = 20 * sc
                    # the box edge (thin) and the text centered (our labels use center alignment)
                    cx = x + w / 2 if True else x
                    dr = ImageDraw.Draw(canvas, "RGBA")
                    colr = col(pr.get("Text Color", "#FFFFFF"))
                    # draw text as a bar of the exact advance width (the glyphs themselves are not needed to check fit)
                    by = H - (y + h2 / 2 + th / 2)
                    bx = (x + w / 2 - tw / 2)
                    dr.rectangle([bx, by, bx + tw, by + th * 0.7], fill=(colr[0], colr[1], colr[2], 170))
                    continue
                img = images.get(pr.get("image", ""))
                if img is None:
                    img = Image.new("RGBA", (16, 16), (255, 255, 255, 255))
                tx, ty_ = int(round(w)), int(round(h2))
                if tx < 1 or ty_ < 1:
                    continue
                tiles = int(pr.get("tileX", 1))
                if tiles > 1:
                    tile = img.resize((max(1, tx // tiles), ty_), Image.NEAREST)
                    scaled = Image.new("RGBA", (tx, ty_))
                    for k in range(tiles):
                        scaled.paste(tile, (k * (tx // tiles), 0))
                else:
                    scaled = img.resize((tx, ty_), Image.NEAREST)
                px = scaled.load()
                for yy in range(ty_):
                    for xx in range(tx):
                        r_, g_, b_, a_ = px[xx, yy]
                        px[xx, yy] = (r_ * tint[0] // 255, g_ * tint[1] // 255, b_ * tint[2] // 255, a_ * tint[3] // 255)
                canvas.alpha_composite(scaled, (int(x), int(H - y - h2))) if 0 <= x and int(H - y - h2) >= 0 and x + tx <= W and H - y - h2 + ty_ <= H else \
                    canvas.paste(scaled, (int(x), int(H - y - h2)), scaled)
            pages.append(canvas)
        if len(pages) == 1:
            pages[0].convert("RGB").save(os.path.join(OUT, tag + ".png"))
        else:
            tw = pages[0].width
            sheet = Image.new("RGB", (tw, sum(p.height for p in pages)))
            yy = 0
            for p in pages:
                sheet.paste(p.convert("RGB"), (0, yy))
                yy += p.height
            sheet.save(os.path.join(OUT, tag + ".png"))
    print("previews: %d PNGs in %s" % (len(tags), OUT))


if __name__ == "__main__":
    sys.exit(main())
