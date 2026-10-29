#!/usr/bin/env python3
"""Offline check of the generated scene-code shapes (no device, no Android build of the app needed).

It proves with plain javac (against the template module's compiled engine classes) that
  1. the NEW generated item shapes compile: element without events (no elementEvents), with one event, with all
     seven events (getName() still usable inside the event code), a light, event code wrapped in trace markers;
  2. the generated per-scene Refs classes (<scene>ItemRefs / <scene>SceneRefs) compile and a Body script / Scene
     script that extend them can use the elements as fields - the generator text is the REAL refsFor() source,
     sliced out of CodeGenerator.java and run against tiny stubs;
  3. bindRefs() fills the fields, old-style scripts (extends ItemScript) are untouched, clashing names (body, stage,
     actor, playerItem, keywords, invalid names) never become fields;
  4. size old shape (git HEAD templates) vs new shape for a sample scene.

The item templates and event signatures are read from CodeGenerator.java itself (string constants), so the test
follows the generator; the loop that decides which events are emitted is a 6-line mirror of CodeGenerator.build().

Needs: javac (JDK 11+), and a previous `gradlew :template:compileDebugJavaWithJavac` (or :app:compileDebugJavaWithJavac).
usage: python3 tools/test_codegen_shapes.py [outdir]        exit code 1 on any failure
"""
import glob
import json
import os
import re
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/tc_codegen"
GEN = os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d/Helpers/CodeGenerator.java")
PLAYER = os.path.join(ROOT, "app/src/main/assets/java/Player.java")
HARNESS = os.path.join(ROOT, "tools/codegen_shapes")

failures = []


def check(ok, what):
    print(("PASS " if ok else "FAIL ") + what)
    if not ok:
        failures.append(what)


def run(cmd, cwd=None):
    r = subprocess.run(cmd, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, universal_newlines=True)
    return r.returncode, r.stdout


# ---------------------------------------------------------------------------------------------- reading the generator
def java_literals(statement):
    """All string literals of a Java statement, unescaped (\\n \\t \\" are the only escapes used)."""
    out = []
    for line in statement.splitlines():
        for m in re.finditer(r'"((?:[^"\\]|\\.)*)"', line):
            out.append(json.loads('"' + m.group(1) + '"'))
    return out


def const(src, name):
    m = re.search(r"private static final String " + name + r"\s*=(.*?);\s*\n", src, re.S)
    if not m:
        raise SystemExit("constant not found: " + name)
    return "".join(java_literals(m.group(1)))


def signatures(src):
    m = re.search(r"EVENT_SIGNATURES\s*=\s*\{(.*?)\};", src, re.S)
    return java_literals(m.group(1))


def jformat(tpl, *args):
    return re.sub(r"%(\d+)\$s", lambda m: str(args[int(m.group(1)) - 1]), tpl)


def git_show(path):
    code, out = run(["git", "show", "HEAD:" + path], cwd=ROOT)
    return out if code == 0 else None


# ---------------------------------------------------------------------------------------------- sample scene
class El:
    def __init__(self, name, deftype="BoxDef", props=None, events=None, light=False, script=None):
        self.name, self.deftype, self.props, self.events, self.light, self.script = name, deftype, props or [], events or {}, light, script


MARK = [0]


def traced(code):
    MARK[0] += 1
    return "/*@s:%d*/\n%s\n/*@e:%d*/\n" % (MARK[0], code, MARK[0])


def new_item(src, el):
    plain, with_ev, sigs = const(src, "defualtItemCode"), const(src, "itemWithEventsCode"), signatures(src)
    light = const(src, "defaultLightCode")
    init = "\n".join("\t\t\t%s_def.%s;" % (el.name, p) for p in el.props)
    if el.light:
        return jformat(light, el.deftype, el.name, init, "")
    methods = ""
    for i, sig in enumerate(sigs):                  # mirror of the loop in CodeGenerator.build()
        code = el.events.get(i, "")
        if code:
            methods += "\t\t\t@Override public void " + sig + " {\n" + code + "\n\t\t\t}\n"
    return jformat(plain if not methods else with_ev, el.deftype, el.name, init, methods)


def old_item(src_old, events_old, el):
    tpl = const(src_old, "defualtItemCode")
    light = const(src_old, "defaultLightCode")
    init = "\n".join("\t\t\t%s_def.%s;" % (el.name, p) for p in el.props)
    if el.light:
        return jformat(light, el.deftype, el.name, init, "")
    ev = [el.events.get(i, "") for i in range(7)] + ['return "%s";' % el.name]
    return jformat(tpl, el.deftype, el.name, init, jformat(events_old, *ev))


def scene_source(scene, elements, item_fn, body_scripts, scene_script):
    vars_ = "PlayerItem " + ",".join(e.name for e in elements if not e.light) + ";"
    lights = [e.name for e in elements if e.light]
    if lights:
        vars_ += "\nLight " + ",".join(lights) + ";"
    code = "".join(item_fn(e) for e in elements)
    for e in elements:
        if e.script:
            code += "\n%s.setScript(new com.star4droid.Game.Scripts.%s.%sScript().setItem(%s).setStage(this));\n" % (e.name, scene, e.script, e.name)
    code += "\nbindScriptRefs();\n"
    code += "\nscoreText.setItemText(\"hi\");\n"
    if scene_script:
        code += "\nsetScript(new com.star4droid.game.SceneScript.%sScript().setStage(this));\nbindScriptRefs();" % scene
    tpl = open(PLAYER).read()
    return jformat(tpl, scene, vars_, code, "", "", "", "", "", "")


def sample_elements():
    ev_all = {
        0: traced('toast("click " + getName() + " " + current.getName());'),
        1: traced('if (event != null) toast(getName());'),
        2: traced('toast("up");'),
        3: traced('current.getBody().setGravityScale(0f);'),
        4: traced("if (current == null) debug(getName());"),
        5: traced('if (body2.getName().equals("ground")) toast(getName());'),
        6: traced('debug("end");'),
    }
    els = [
        El("background", props=['type="UI"', "width=720.0f", "height=1506.0f", 'image="/color.png"', 'Version="1.0"'], events={}),
        El("player", props=['Version="1.0"', "x=10.0f"], events={0: traced('toast("jump");')}),
        El("ground", props=['Version="1.0"', 'type="STATIC"']),
        El("enemy1", "CircleDef", props=['Version="1.0"'], events=ev_all, script="enemy"),
        El("coin1", props=['Version="1.0"'], events={5: traced('coin1.destroy();')}),
        El("jumpBtn", props=['Version="1.0"', 'type="UI"'], events={0: traced('findItem("player").getBody().setLinearVelocity(0f, 450f * 0.25f);')}),
        El("scoreText", "TextDef", props=['Version="1.0"', 'Text="0"']),
        El("sun", "LightDef", props=['Version="1.0"'], light=True),
    ]
    return els


def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    classes_dirs = glob.glob(os.path.join(ROOT, "template/build/intermediates/javac/*/compile*JavaWithJavac/classes"))
    marker = "com/star4droid/template/ui/UINodeRef.class"
    # newest compiled copy of the engine that already contains the new classes (older release builds may lack them)
    classes_dirs.sort(key=lambda d: os.path.getmtime(os.path.join(d, marker)) if os.path.exists(os.path.join(d, marker)) else 0, reverse=True)
    if not classes_dirs:
        raise SystemExit("no compiled template classes: run  bash gradlew :template:compileDebugJavaWithJavac  first")
    jars = glob.glob(os.path.expanduser("~/.gradle/caches/modules-2/files-2.1/com.badlogicgames.gdx/gdx/*/*/gdx-1*.jar"))
    jars = [j for j in jars if "sources" not in j]
    jars += [j for j in glob.glob(os.path.expanduser("~/.gradle/caches/modules-2/files-2.1/com.badlogicgames.gdx/gdx-box2d/*/*/gdx-box2d-1*.jar")) if "sources" not in j]
    jars.append(os.path.join(ROOT, "app/libs/box2dlights-1.5.jar"))
    # gson: refsFor() reads the .ui files of UI holders (UIDocument) to generate the <holder>_<node> fields
    jars += [j for j in sorted(glob.glob(os.path.expanduser("~/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/*/*/gson-2*.jar"))) if "sources" not in j][-1:]
    cp_engine = classes_dirs[0] + ":" + ":".join(jars)
    check(os.path.exists(os.path.join(classes_dirs[0], "com/star4droid/star2d/ElementDefs/ElementEventAdapter.class")),
          "ElementEventAdapter is part of the compiled template classes")

    src = open(GEN).read()
    els = sample_elements()
    java_dir = os.path.join(OUT, "project", "java")
    os.makedirs(os.path.join(java_dir, "com/star4droid/Game/Scripts/scene1"))
    os.makedirs(os.path.join(java_dir, "com/star4droid/Game/SceneScript"))

    # ---- 1. refs generator: the REAL source of CodeGenerator.refsFor, run against stubs --------------------------------------
    start = src.index("    // ------------------------------------------------------------------ generated Refs base classes")
    end = src.index("    /** Generated Java of one scene. */")
    block = src[start:end]
    hdir = os.path.join(OUT, "harness")
    pkg = os.path.join(hdir, "com/star4droid/star2d/Helpers")
    for d in ("editor", "../prefab", "../editor"):
        os.makedirs(os.path.normpath(os.path.join(pkg, d)), exist_ok=True)
    open(os.path.join(pkg, "PropertySet.java"), "w").write(
        "package com.star4droid.star2d.Helpers;\npublic class PropertySet<K,V> extends java.util.HashMap<K,V> {\n"
        "  public String getString(Object k){ Object v = get(k); return v == null ? \"\" : v.toString(); } }\n")
    open(os.path.join(pkg, "editor/Project.java"), "w").write(
        "package com.star4droid.star2d.Helpers.editor;\npublic class Project { String p; public Project(String p){ this.p = p; } public String getPath(){ return p; } }\n")
    open(os.path.join(hdir, "com/star4droid/star2d/prefab/PrefabStore.java"), "w").write(
        "package com.star4droid.star2d.prefab;\npublic class PrefabStore { public static boolean isTempScene(String s){ return s.startsWith(\"__prefab_\"); } }\n")
    open(os.path.join(hdir, "com/star4droid/star2d/editor/Utils.java"), "w").write(
        "package com.star4droid.star2d.editor;\npublic class Utils { public static String getStackTraceString(Throwable t){ return String.valueOf(t); } }\n")
    open(os.path.join(pkg, "RefsGenHarness.java"), "w").write(
        "package com.star4droid.star2d.Helpers;\n"
        "import com.badlogic.gdx.Gdx;\nimport com.badlogic.gdx.files.FileHandle;\nimport com.star4droid.star2d.editor.Utils;\nimport java.util.ArrayList;\n"
        "public class RefsGenHarness {\n"
        "  public static final class SceneCode { public String scene, path, code; }\n"
        "  static ArrayList<PropertySet<String, Object>> loadProperties(com.star4droid.star2d.Helpers.editor.Project p, String s){ return new ArrayList<PropertySet<String, Object>>(); }\n"
        + block + "}\n")
    sources = glob.glob(hdir + "/**/*.java", recursive=True) + [os.path.join(HARNESS, "RefsRun.java")]
    hcls = os.path.join(OUT, "harness_classes")
    os.makedirs(hcls)
    code, out = run(["javac", "-nowarn", "--release", "8", "-cp", cp_engine, "-d", hcls] + sources)
    check(code == 0, "refsFor() (real CodeGenerator source) compiles standalone")
    if code:
        print(out)
        return
    refs_root = os.path.join(java_dir, "com/star4droid/Game/Refs")
    code, out = run(["java", "-cp", hcls + ":" + cp_engine, "RefsRun", os.path.join(OUT, "project")])
    print(out.rstrip())
    check(code == 0, "RefsRun executed")
    for want in ("scene1ItemRefs.java", "scene1SceneRefs.java", "level2ItemRefs.java", "level2SceneRefs.java"):
        check(os.path.exists(os.path.join(refs_root, want)), "generated " + want)
    check(not os.path.exists(os.path.join(refs_root, "__prefab_xItemRefs.java")), "temporary prefab scene gets no Refs")
    check(not os.path.exists(os.path.join(refs_root, "1badItemRefs.java")), "invalid scene name gets no Refs")
    text = open(os.path.join(refs_root, "scene1ItemRefs.java")).read()
    print(text)
    check("public PlayerItem background, player, ground, enemy1, coin1, jumpBtn, scoreText, lifeBar, joy, grp;" in text, "all element fields are declared once (duplicate 'player' ignored)")
    check("public Light sun;" in text and 'sun = findLight("sun");' in text, "lights become Light fields")
    for bad in ("body", "stage", "actor", "playerItem", "class", "my-item"):
        check(not re.search(r"\b" + re.escape(bad) + r"\b\s*[,;=]", text.split("bindRefs")[0].split("public abstract class")[1]), "no field " + bad)
    check("->" not in text and "<>" not in text, "Refs source uses no Java 8 syntax (the game compiles at source level 7)")
    check("public UIHolder hud;" in text and "hud" not in text.split("public PlayerItem")[1].split(";")[0], "a UI holder is a typed UIHolder field, not a PlayerItem")
    check("public UINodeRef hud_root, hud_btnPlay, hud_score, hud_player;" in text, "inner nodes of the holder (file in a sub folder) become <holder>_<id> fields; invalid ids are skipped")
    check('hud_btnPlay = hud == null ? null : hud.node("btnPlay");' in text and 'hud = (UIHolder) items.get("hud");' in text, "holder and node fields are bound")
    check("hud_bad" not in text.split("bindRefs")[0].split("public abstract class")[1], "no field for the id 'bad-id'")

    # ---- 2. scene sources in the NEW shape, scripts extending Refs ----------------------------------------------------------
    scripts = os.path.join(java_dir, "com/star4droid/Game/Scripts/scene1")
    open(os.path.join(scripts, "enemyScript.java"), "w").write(
        "package com.star4droid.Game.Scripts.scene1;\n"
        "import com.star4droid.template.Utils.PlayerItem;\n"
        "public class enemyScript extends com.star4droid.Game.Refs.scene1ItemRefs {\n"
        "    public void onBodyUpdate(){\n"
        "        if (playerItem.distTo(player) < 100f) coin1.destroy();\n"
        "        item(\"jumpBtn\").getActor().setVisible(false);\n"
        "        body.setActive(true);                       // ItemScript.body (not hidden by an element called body)\n"
        "        stage.toast(\"x\");\n"
        "        ground.getBody().setGravityScale(1f);\n"
        "        hud_btnPlay.setText(\"Go\").setVisible(true);          // generated typed accessor of an inner UI element\n"
        "        hud.node(\"score\").setText(\"0\");\n"
        "        hud.onClick(\"btnPlay\", new Runnable(){ public void run(){ coin1.destroy(); } });\n"
        "        hud_score.onChange(new com.star4droid.template.ui.UIListeners.OnValue(){ public void on(Object v){} });\n"
        "    }\n"
        "    public void onCollisionBegin(PlayerItem other){ if (other == player) findItem(\"ground\"); }\n"
        "}\n")
    open(os.path.join(scripts, "oldStyleScript.java"), "w").write(
        "package com.star4droid.Game.Scripts.scene1;\n"
        "import com.star4droid.template.Utils.ItemScript;\n"
        "public class oldStyleScript extends ItemScript {\n"
        "    public void onBodyUpdate(){ getStage().findItem(\"player\"); findItem(\"coin1\"); item(\"coin1\"); }\n"
        "}\n")
    open(os.path.join(java_dir, "com/star4droid/Game/SceneScript/scene1Script.java"), "w").write(
        "package com.star4droid.game.SceneScript;\n"
        "public class scene1Script extends com.star4droid.Game.Refs.scene1SceneRefs {\n"
        "    public void create(){ player.getBody().setGravityScale(-1f); ground.destroy(); item(\"coin1\"); }\n"
        "    public void draw(){ if (enemy1 != null) enemy1.getActor().toFront(); }\n"
        "    public void pause(){}\n    public void resume(){}\n"
        "}\n")
    new_src = scene_source("scene1", els, lambda e: new_item(src, e), True, True)
    gdir = os.path.join(java_dir, "com/star4droid/Game")
    open(os.path.join(gdir, "scene1.java"), "w").write(new_src)
    print("---- generated scene1.java (new shape), first lines ----")
    print("\n".join(new_src.splitlines()[:70]))
    allsrc = glob.glob(java_dir + "/**/*.java", recursive=True)
    gcls = os.path.join(OUT, "game_classes")
    os.makedirs(gcls)
    code, out = run(["javac", "-nowarn", "--release", "8", "-cp", cp_engine, "-d", gcls] + allsrc)
    if code:
        print(out)
    check(code == 0, "generated scene (no events / one event / all events / light / trace markers) + Refs + scripts compile together")
    if code:
        return
    check("elementEvents" not in re.search(r"background_def.*?background = ", new_src, re.S).group(0), "element without events emits no elementEvents")
    check(new_src.count("new ElementEventAdapter(") == 4, "only the 4 elements with events get an ElementEventAdapter")
    pl = re.search(r"player_def\.elementEvents.*?player = ", new_src, re.S).group(0)
    check(pl.count("@Override") == 1 and "onClick" in pl, "an element with one event overrides exactly one method")

    # ---- 3. bindRefs behaviour -------------------------------------------------------------------------------------------
    bsrc = os.path.join(OUT, "BindRunCopy")
    os.makedirs(bsrc)
    shutil.copy(os.path.join(HARNESS, "BindRun.java"), bsrc)
    bcls = os.path.join(OUT, "bind_classes")
    os.makedirs(bcls)
    code, out = run(["javac", "-nowarn", "--release", "8", "-cp", gcls + ":" + cp_engine, "-d", bcls, os.path.join(bsrc, "BindRun.java")])
    check(code == 0, "BindRun compiles")
    if code:
        print(out)
        return
    code, out = run(["java", "-cp", bcls + ":" + gcls + ":" + cp_engine, "BindRun"])
    print(out.rstrip())
    check(code == 0 and "FAIL" not in out, "bindRefs() fills the fields; old-style scripts unaffected; clashing names skipped")

    # ---- 4. size old vs new -------------------------------------------------------------------------------------------------
    old_gen = git_show("app/src/main/java/com/star4droid/star2d/Helpers/CodeGenerator.java")
    old_ev = git_show("app/src/main/assets/java/events.java")
    if old_gen and old_ev and "itemWithEventsCode" not in old_gen:
        big = []
        for i in range(30):
            big.append(El("plain%d" % i, props=['Version="1.0"', "x=%d.0f" % i, "y=3.0f"]))
        for i in range(10):
            big.append(El("btn%d" % i, props=['Version="1.0"', "x=%d.0f" % i], events={0: 'toast("hit");'}))
        old_total = sum(len(old_item(old_gen, old_ev, e)) for e in big)
        new_total = sum(len(new_item(src, e)) for e in big)
        old_lines = sum(old_item(old_gen, old_ev, e).count("\n") for e in big)
        new_lines = sum(new_item(src, e).count("\n") for e in big)
        print("sample scene, 40 elements (30 without events, 10 with onClick): old %d bytes / %d lines  ->  new %d bytes / %d lines"
              % (old_total, old_lines, new_total, new_lines))
        check(new_total < old_total * 0.6, "new shape is clearly shorter")
        # classes / methods the compiler has to produce
        print("anonymous event classes: old %d, new %d;  event methods: old %d, new %d" % (40, 10, 40 * 8, 10))
    else:
        print("(old templates not available from git HEAD: size comparison skipped)")

    print("\nRESULT:", "ALL CHECKS PASSED" if not failures else "%d FAILED" % len(failures))
    for f in failures:
        print("  -", f)


main()
sys.exit(1 if failures else 0)
