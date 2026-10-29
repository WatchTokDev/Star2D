#!/usr/bin/env python3
"""Offline check of the font presets (assets/fonts/presets.json) with the REAL FreeType: parses them with the editor's FontPresets class
and renders a contact sheet PNG. Usage: python3 tools/verify_fonts.py [out.png] ["sample text"]"""
import glob, os, subprocess, sys, tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
def jar(pattern):
    r = sorted(glob.glob(os.path.expanduser("~/.gradle/caches/modules-2/files-2.1/**/" + pattern), recursive=True))
    if not r: sys.exit("missing jar " + pattern + " (run a gradle build once)")
    return r[0]

cp = ":".join([jar("gdx-1.14.2.jar"), jar("gdx-platform-1.14.2-natives-desktop.jar"), jar("gdx-freetype-1.14.2.jar"),
               jar("gdx-freetype-platform-1.14.2-natives-desktop.jar"), jar("gdx-jnigen-loader-*.jar"), jar("gson-2*.jar")])
out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(tempfile.gettempdir(), "fonts_sheet.png")
build = tempfile.mkdtemp(prefix="fontcheck")
srcs = [os.path.join(ROOT, "tools/fontcheck/FontSheet.java"), os.path.join(ROOT, "app/src/main/java/com/star4droid/star2d/fonts/FontPresets.java")]
subprocess.check_call(["javac", "-nowarn", "-cp", cp, "-d", build] + srcs)
cmd = ["java", "-Djava.awt.headless=true", "-cp", build + ":" + cp, "FontSheet",
       os.path.join(ROOT, "app/src/main/assets/fonts/presets.json"), os.path.join(ROOT, "app/src/main/assets/files"), out]
if len(sys.argv) > 2: cmd.append(sys.argv[2])
subprocess.check_call(cmd)
