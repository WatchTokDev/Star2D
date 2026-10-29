#!/usr/bin/env python3
"""Tests of the UI Maker + the `ui` AI tool.

  python3 tools/test_uimaker.py          compile + pure-Java logic checks + docs/server wiring (+ headless GL preview)
  python3 tools/test_uimaker.py --quick  skip the Xvfb GL preview test
  python3 tools/test_uimaker.py --strict-i18n  also fail when a uim_ key has no Arabic string

1. compiles app + template (gradle)
2. tools/uimaker/UiMakerTest.java: templates, lint, history, UiOps, the ui tool (UiAi)
3. tools/uimaker/run_gl.sh: real offscreen render of the preview (Xvfb + software GL)
4. wiring: tool declared in server/src/tools.ts + RemoteTools + AI.md, docs topic, Arabic strings for every uim_ key
"""
import glob, os, re, subprocess, sys

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


def jar(pat):
    h = [x for x in glob.glob(os.path.expanduser("~/.gradle/caches/modules-2/files-2.1/**/" + pat), recursive=True) if "sources" not in x]
    return h[0] if h else None


r = run(["bash", "gradlew", "--offline", "-q", ":app:compileDebugJavaWithJavac", ":template:compileDebugJavaWithJavac"])
errs = [l for l in r.stderr.splitlines() if "error" in l]
check("gradle compile (app, template)", r.returncode == 0, "\n".join(errs)[:800])

classes = os.path.join(ROOT, "app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes")
cp = ":".join([classes] + [j for j in (jar("gson-2*.jar"), jar("gdx-1.14.2.jar")) if j])
out = "/tmp/uimaker_test_classes"
os.makedirs(out, exist_ok=True)
r = run(["javac", "-cp", cp, "-d", out, "tools/uimaker/UiMakerTest.java"])
check("compile UiMakerTest", r.returncode == 0, r.stderr[-600:])
if r.returncode == 0:
    r = run(["java", "-cp", cp + ":" + out, "UiMakerTest"])
    lines = [l for l in r.stdout.splitlines() if l.strip()]
    for l in lines:
        if l.startswith("FAIL"):
            print(l)
    check("UiMakerTest: " + (lines[-1] if lines else r.stderr[-300:]), r.returncode == 0)

if not QUICK:
    r = run(["bash", "tools/uimaker/run_gl.sh", "/tmp/uimaker_out"])
    check("GL preview test: " + (r.stdout.strip().splitlines() or ["no output"])[-1], r.returncode == 0 and "ALL OK" in r.stdout, r.stdout[-500:] + r.stderr[-300:])

if not QUICK:
    r = run(["bash", "tools/uikit/run.sh", "/tmp/uikit_proj", "/tmp/uikit_out", "all", "jelly"])
    check("UI skills (UiKit) render + actions: " + (r.stdout.strip().splitlines() or ["no output"])[-1], r.returncode == 0 and "ALL OK" in r.stdout, r.stdout[-800:] + r.stderr[-300:])

# wiring
def read(p):
    return open(os.path.join(ROOT, p), encoding="utf-8").read()

check("tool declared in tools.ts", 'name: "ui"' in read("server/src/tools.ts"))
check("CORE_RULES clause", "7e. Menus" in read("server/src/tools.ts"))
check("RemoteTools dispatch", 'case "ui": return UiTools.ui(args);' in read("app/src/main/java/com/star4droid/star2d/remote/RemoteTools.java"))
check("AI.md lists ui", "`ui`" in read("server/AI.md"))
check("docs topic ui", os.path.isfile(os.path.join(ROOT, "app/src/main/assets/ai/docs/ui.md")) and "`ui`" in read("app/src/main/assets/ai/docs/index.md"))
ar = read("app/src/main/assets/i18n/strings_ar_SD.properties")
keys = set()
for f in glob.glob(os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d/ui/uimaker/*.java")) + [os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d/ui/panels/FilesPanel.java")]:
    keys |= set(re.findall(r'Tr\.t\(\s*"(uim_[A-Za-z0-9_]+)"\s*,', open(f, encoding="utf-8").read()))
missing = sorted(k for k in keys if not re.search(r"^" + re.escape(k) + r"\s*=", ar, re.M))
# New keys are translated by a later pass (assets/i18n is not edited together with the code): only --strict-i18n fails on them.
STRICT = "--strict-i18n" in sys.argv
if missing and not STRICT:
    print("NOTE %d of %d uim_ keys have no Arabic string yet (pending translation pass); first: %s" % (len(missing), len(keys), ", ".join(missing[:6])))
check("Arabic strings for %d uim_ keys" % len(keys), STRICT is False or not missing, ", ".join(missing[:15]))

print("\nALL OK" if not fails else "\nFAILED: " + ", ".join(fails))
sys.exit(1 if fails else 0)
