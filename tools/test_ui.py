#!/usr/bin/env python3
"""Tests of the .ui format + runtime (docs/UI.md).

  python3 tools/test_ui.py            all checks
  python3 tools/test_ui.py --quick    skip the headless libGDX run and the PNG previews

1. compiles app + template + desktop (gradle)
2. UiTest.java: registry set/get for every property, JSON round trip, layout math, samples parse, themes
3. icon ids of UIWidgetRegistry exist in assets/icons/catalog.json
4. every sample renders in the AWT preview (PNG written to /tmp/uiout)
5. the real runtime under Xvfb + Mesa (tools/ui/run_gdx.sh): events, sliders, text fields, scroll, tooltip
6. docs/UI.md widget reference is up to date with the registry
"""
import glob, json, os, re, subprocess, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
QUICK = "--quick" in sys.argv
env = dict(os.environ, ANDROID_HOME=os.environ.get("ANDROID_HOME", "/opt/android-sdk"))
fails = []


def run(cmd, **kw):
    return subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, env=env, **kw)


def check(name, ok, detail=""):
    print(("OK   " if ok else "FAIL ") + name + ((" - " + detail) if detail and not ok else ""))
    if not ok:
        fails.append(name)


def classpath():
    cp = [os.path.join(ROOT, "app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes")]
    for pat in ("gson-2*.jar", "gdx-1.14.2.jar"):
        h = [x for x in glob.glob(os.path.expanduser("~/.gradle/caches/modules-2/files-2.1/**/" + pat), recursive=True) if "sources" not in x]
        if h:
            cp.append(h[0])
    return ":".join(cp)


# 1 compile
r = run(["bash", "gradlew", "--offline", "-q", ":app:compileDebugJavaWithJavac", ":template:compileDebugJavaWithJavac", ":desktop:compileJava"])
check("gradle compile (app, template, desktop)", r.returncode == 0, "\n".join(l for l in r.stderr.splitlines() if "error" in l)[:800])

# 2 UiTest
out = "/tmp/uitest_classes"
os.makedirs(out, exist_ok=True)
cp = classpath()
r = run(["javac", "-cp", cp, "-d", out, "tools/ui/UiTest.java"])
check("compile UiTest", r.returncode == 0, r.stderr[-500:])
if r.returncode == 0:
    r = run(["java", "-cp", cp + ":" + out, "UiTest", "docs/ui-samples"])
    lines = [l for l in r.stdout.splitlines() if l.strip()]
    for l in lines:
        if l.startswith("FAIL"):
            print(l)
    check("UiTest: " + (lines[-1] if lines else "no output"), r.returncode == 0)

# 3 icon ids
cat = {i["i"] for i in json.load(open(os.path.join(ROOT, "app/src/main/assets/icons/catalog.json")))["icons"]}
src = open(os.path.join(ROOT, "app/src/main/java/com/star4droid/template/ui/UIWidgetRegistry.java")).read()
used = set(re.findall(r'w\(UINode\.\w+, "[^"]*", "([a-z0-9-]+)"', src))
check("registry icon ids exist in the icon library (%d)" % len(used), used <= cat, str(sorted(used - cat)))

if not QUICK:
    # 4 AWT preview of the samples
    os.makedirs("/tmp/uiout", exist_ok=True)
    r = run(["javac", "-cp", cp, "-d", out, "tools/ui/UiPreview.java"])
    if r.returncode == 0:
        for f in sorted(glob.glob(os.path.join(ROOT, "docs/ui-samples/*.ui"))):
            png = "/tmp/uiout/" + os.path.basename(f)[:-3] + ".png"
            r = run(["java", "-cp", cp + ":" + out, "UiPreview", f, png])
            check("preview " + os.path.basename(f), r.returncode == 0 and os.path.exists(png), r.stderr[-300:])
    else:
        check("compile UiPreview", False, r.stderr[-300:])

    # 5 real runtime
    proj = "/tmp/uiproj"
    os.makedirs(proj + "/UI", exist_ok=True)
    for f in glob.glob(os.path.join(ROOT, "docs/ui-samples/*.ui")):
        subprocess.run(["cp", f, proj + "/UI/"])
    for name in ("hud", "settings", "levels"):
        r = run(["bash", "tools/ui/run_gdx.sh", proj, name, "/tmp/uigdx/%s.png" % name, "test"], timeout=300)
        txt = r.stdout + r.stderr
        bad = [l for l in txt.splitlines() if l.startswith("FAIL") or "Exception" in l]
        check("runtime test %s: %s" % (name, "ALL OK" if "ALL OK" in txt else "see log"), "ALL OK" in txt and not bad, "\n".join(bad[:5]))

# 6 docs up to date
before = open(os.path.join(ROOT, "docs/UI.md"), encoding="utf-8").read()
r = run(["python3", "tools/ui/gen_ui_docs.py"])
after = open(os.path.join(ROOT, "docs/UI.md"), encoding="utf-8").read()
check("docs/UI.md widget reference matches the registry", r.returncode == 0 and before == after, "re-run: python3 tools/ui/gen_ui_docs.py (file updated)")

print("\n%s" % ("ALL OK" if not fails else "%d FAILED: %s" % (len(fails), ", ".join(fails))))
sys.exit(1 if fails else 0)
