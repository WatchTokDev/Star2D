#!/usr/bin/env python3
"""Regenerates the "Widget reference" part of docs/UI.md from UIWidgetRegistry.describe().

usage: python3 tools/ui/gen_ui_docs.py            (needs :app:compileDebugJavaWithJavac done once)
The text between <!-- BEGIN WIDGETS --> and <!-- END WIDGETS --> in docs/UI.md is replaced.
"""
import json, os, subprocess, sys, glob, tempfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
CLASSES = os.path.join(ROOT, "app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes")


def jars():
    out = []
    for pat in ("gson-2*.jar", "gdx-1.14.2.jar"):
        hits = glob.glob(os.path.expanduser("~/.gradle/caches/modules-2/files-2.1/**/" + pat), recursive=True)
        hits = [h for h in hits if "sources" not in h]
        if hits:
            out.append(hits[0])
    return out


def describe():
    tmp = tempfile.mkdtemp()
    cp = ":".join([CLASSES] + jars())
    src = os.path.join(ROOT, "tools/ui/DumpRegistry.java")
    subprocess.check_call(["javac", "-cp", cp, "-d", tmp, src], stderr=subprocess.DEVNULL)
    res = subprocess.run(["java", "-cp", cp + ":" + tmp, "DumpRegistry"], capture_output=True, text=True)
    txt = res.stdout
    return json.loads(txt[txt.index("{"):])


def esc(s):
    return str(s).replace("|", "\\|").replace("\n", " ")


def table(props):
    rows = ["| key | where | kind | default | range / options | description |", "|---|---|---|---|---|---|"]
    for p in props:
        rng = ""
        if "options" in p:
            rng = ", ".join(p["options"])
        elif "min" in p or "max" in p:
            rng = "%s..%s" % (p.get("min", ""), p.get("max", ""))
            if "step" in p:
                rng += " step %s" % p["step"]
        rows.append("| `%s` | %s | %s | `%s` | %s | %s |" % (
            p["key"], p["where"], p["kind"], esc(p["default"]), esc(rng), esc(p["description"])))
    return "\n".join(rows)


def build(d):
    out = []
    out.append("### Common properties (every node)\n")
    out.append(table(d["common"]))
    out.append("")
    for t in d["types"]:
        out.append("### %s  (`%s`)\n" % (t["name"], t["type"]))
        out.append("%s  \nIcon id: `%s` · palette category: **%s** · container: %s · events: %s\n" % (
            t["description"], t["icon"], t["category"], "yes" if t["container"] else "no",
            ", ".join("`%s`" % e for e in t["events"]) or "none"))
        if t["styles"]:
            out.append("Style presets (`props.style`): " + ", ".join("`%s`" % s for s in t["styles"]) + "\n")
        if t["props"]:
            out.append(table(t["props"]))
        out.append("")
    out.append("Theme presets: " + ", ".join("`%s`" % s for s in d["themePresets"]) + "\n")
    return "\n".join(out)


def main():
    d = describe()
    p = os.path.join(ROOT, "docs/UI.md")
    s = open(p, encoding="utf-8").read()
    a, b = "<!-- BEGIN WIDGETS -->", "<!-- END WIDGETS -->"
    i, j = s.index(a) + len(a), s.index(b)
    s = s[:i] + "\n\n" + build(d) + "\n" + s[j:]
    open(p, "w", encoding="utf-8").write(s)
    print("widget reference updated:", len(d["types"]), "types")


if __name__ == "__main__":
    main()
