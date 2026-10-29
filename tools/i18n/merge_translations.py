#!/usr/bin/env python3
"""Appends translations to assets/i18n/strings_<lang>.properties.

  python3 tools/i18n/merge_translations.py ar fr br ru es

Input: /tmp/i18n_missing_<lang>.tsv (from dump_keys.py: key<TAB>english) and /tmp/i18n_out_<lang>_*.json ({english: translated}).
Only keys that are still missing are appended, under a comment block; existing lines are never changed.
"""
import glob, json, os, re, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FILES = {"ar": "strings_ar_SD", "fr": "strings_fr_US", "br": "strings_br_US", "ru": "strings_ru_US", "es": "strings_es_US"}
PH = re.compile(r"%(?:\d+\$)?[sdf%]|\{\d+\}")


def main():
    for lang in sys.argv[1:]:
        tr = {}
        for f in sorted(glob.glob("/tmp/i18n_out_%s_*.json" % lang)):
            tr.update(json.load(open(f, encoding="utf-8")))
        path = os.path.join(ROOT, "app/src/main/assets/i18n", FILES[lang] + ".properties")
        have = set()
        for line in open(path, encoding="utf-8"):
            m = re.match(r"\s*([^=:\s#!][^=:\s]*)\s*[=:]", line)
            if m:
                have.add(m.group(1))
        add, miss, badph = [], [], []
        for line in open("/tmp/i18n_missing_%s.tsv" % lang, encoding="utf-8"):
            k, _, en = line.rstrip("\n").partition("\t")
            if k in have or not en.strip():
                continue
            v = tr.get(en)
            if not v or not v.strip():
                miss.append(k)
                continue
            if sorted(PH.findall(en)) != sorted(PH.findall(v)):
                badph.append((k, en, v))
                v = en if False else v
            v = v.replace("\r", "").replace("\n", "\\n").strip()
            add.append("%s = %s" % (k, v))
        text = open(path, encoding="utf-8").read()
        if not text.endswith("\n"):
            text += "\n"
        text += "\n# ---- v22: UI Maker, behaviors, script variables, fonts, support (auto-merged)\n" + "\n".join(add) + "\n"
        open(path, "w", encoding="utf-8", newline="\n").write(text)
        print("%s: +%d lines, untranslated %d, placeholder mismatch %d" % (lang, len(add), len(miss), len(badph)))
        for k, en, v in badph[:10]:
            print("   PH", k, "|", en, "|", v)
        if miss:
            print("   missing:", miss[:10])


main()
