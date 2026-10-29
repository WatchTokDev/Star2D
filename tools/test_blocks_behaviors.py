#!/usr/bin/env python3
"""Tests of the behavior blocks and block descriptions (no phone needed):
  1. compiles the Android-free core (app/.../star2d/blocks/*.java) against the template runtime,
  2. BlocksTest.java: reflection == textual scanner for every built-in @Block, the sample project behavior
     (tools/blocks_test/samples/Health.java), scene filter, multi-language descriptions (old files stay valid),
  3. compiles the generated Java of every block (statement + value blocks, built-in and project behaviors) and of the
     fixed "Behaviors" blocks read out of BlockCatalog.java, against the template classes (Java 8 source level).
Needs: bash gradlew :template:compileDebugJavaWithJavac once (and the gradle cache for gdx / gson).
"""
import glob, json, os, re, subprocess, sys, tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def find(pattern):
    r = glob.glob(os.path.expanduser(pattern), recursive=True)
    return sorted(r)[-1] if r else None


def main():
    cache = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")
    jars = [find(cache + "/com.badlogicgames.gdx/gdx/*/*/gdx-1*.jar"), find(cache + "/com.badlogicgames.gdx/gdx-box2d/*/*/gdx-box2d-1*.jar"),
            find(cache + "/com.google.code.gson/gson/*/*/gson-2*.jar"), find(cache + "/com.badlogicgames.box2dlights/box2dlights/*/*/box2dlights-*.jar")]
    jars = [j for j in jars if j and "sources" not in j]
    classes = os.path.join(ROOT, "template/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes")
    if not os.path.isdir(classes):
        print("template classes missing: run  bash gradlew :template:compileDebugJavaWithJavac")
        return 2
    tmp = tempfile.mkdtemp(prefix="s2_blocks_test_")
    out = os.path.join(tmp, "out")
    os.makedirs(out)
    cp = os.pathsep.join([classes] + jars)
    srcs = glob.glob(os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d/blocks/*.java")) + [os.path.join(ROOT, "tools/blocks_test/BlocksTest.java")]
    r = subprocess.run(["javac", "-nowarn", "-encoding", "UTF-8", "-d", out, "-cp", cp] + srcs, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout + r.stderr)
        return 1
    gen = os.path.join(tmp, "generated.txt")
    r = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-Dgen.out=" + gen, "-cp", os.pathsep.join([out, cp]), "BlocksTest", ROOT], capture_output=True, text=True)
    print(r.stdout + r.stderr)
    if r.returncode != 0:
        return 1

    # ---- generated code of the @Block blocks must compile
    code = open(gen, encoding="utf-8").read()
    # ---- fixed blocks: read their templates out of BlockCatalog.java and fill sample values
    cat = open(os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d/ui/blocks/BlockCatalog.java"), encoding="utf-8").read()

    def jstr(x):
        return json.loads('"' + x + '"')

    def fill(tpl, vals):
        return re.sub(r"%(\d+)\$s", lambda m: vals[int(m.group(1)) - 1] if int(m.group(1)) - 1 < len(vals) else "", tpl)

    fixed = []
    for m in re.finditer(r'def\("Behaviors", beh, "(\w+)", "([^"]*)", "([^"]*)", false, "((?:[^"\\]|\\.)*)"\);', cat):
        title, fields, defaults, tpl = m.group(1), m.group(2).split(), m.group(3), jstr(m.group(4))
        vals = ["player"] + ["abc" if f.startswith("behavior") else "true" if "Boolean" in f else "speed" if f.startswith("setting") else "8" for f in fields[1:]]
        # quoted ids in the templates are written by the template itself; the field values are raw
        fixed.append("// fixed block " + title + "\n" + fill(tpl, vals))
    nfixed = len(fixed)
    reps = []
    for m in re.finditer(r'r\("Behaviors", "([^"]*)", "((?:[^"\\]|\\.)*)"\);', cat):
        disp, tpl = m.group(1), jstr(m.group(2))
        slots = disp.count("___")
        vals = ["player", "Health", "speed"][:slots] if "setting" not in disp else ["speed", "Health", "player"]
        if disp.startswith("behavior ___ of"):
            vals = ["Health", "player"]
        expr = fill(tpl, vals)
        reps.append("Object rep%d = %s; // %s" % (len(reps), expr, disp))
    nrep = len(reps)
    if nfixed < 6 or nrep < 6:
        print("FAIL: fixed behavior blocks not found in BlockCatalog.java (%d statements, %d values)" % (nfixed, nrep))
        return 1
    print("fixed blocks: %d action blocks, %d value blocks" % (nfixed, nrep))

    wrap = ("package com.star4droid.Game;\nimport com.star4droid.template.Items.*;\nimport com.star4droid.template.Utils.PlayerItem;\n"
            "public class GenCheck extends StageImp {\n  public void gen() {\n    PlayerItem player = null, enemy1 = null;\n"
            + code + "\n" + "\n".join(fixed) + "\n" + "\n".join(reps) + "\n  }\n}\n")
    gdir = os.path.join(tmp, "gen/com/star4droid/Game")
    os.makedirs(gdir, exist_ok=True)
    open(os.path.join(gdir, "GenCheck.java"), "w", encoding="utf-8").write(wrap)
    bdir = os.path.join(tmp, "gen/com/star4droid/Game/Behaviors")
    os.makedirs(bdir)
    open(os.path.join(bdir, "Health.java"), "w", encoding="utf-8").write(open(os.path.join(ROOT, "tools/blocks_test/samples/Health.java"), encoding="utf-8").read())
    if os.environ.get("BLOCKS_VERBOSE"):
        print(wrap)
    cls = os.path.join(tmp, "genout")
    os.makedirs(cls)
    files = glob.glob(os.path.join(tmp, "gen/**/*.java"), recursive=True)
    r = subprocess.run(["javac", "-nowarn", "-source", "8", "-target", "8", "-Xlint:-options", "-encoding", "UTF-8", "-d", cls, "-cp", cp] + files, capture_output=True, text=True)
    if r.returncode != 0:
        print("FAIL: generated block code does not compile")
        print(r.stdout + r.stderr)
        print("---- generated:\n" + wrap)
        return 1
    print("generated code of %d lines compiles (Java 8)" % wrap.count("\n"))
    print("ALL OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
