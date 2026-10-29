#!/usr/bin/env python3
"""Renders every library icon id used by UiKit into <project>/images/ui_<id>.png (white, 128 px) for the headless kit tests."""
import json, os, re, sys
import cairosvg
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
proj = sys.argv[1]
cat = {i["i"]: i for i in json.load(open(ROOT + "/app/src/main/assets/icons/catalog.json"))["icons"]}
src = open(ROOT + "/app/src/main/java/com/star4droid/star2d/ui/uimaker/UiKit.java").read()
ids = set(re.findall(r'"([a-z0-9]+(?:-[a-z0-9]+)*)"', src)) & set(cat)
os.makedirs(proj + "/images", exist_ok=True)
for i in sorted(ids):
    ic = cat[i]
    w = ic.get("w", 24)
    svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="128" height="128"><path fill="#ffffff" d="%s"/></svg>' % (w, w, ic["p"])
    cairosvg.svg2png(bytestring=svg.encode(), write_to=proj + "/images/ui_" + i.replace("-", "_") + ".png")
print(len(ids), "icons")
