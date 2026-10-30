#!/usr/bin/env python3
"""Merges translated texts of the built-in behavior blocks into assets/i18n/strings_<lang>.properties.
Input: blocks.tsv made by a dump of BehaviorBlocks.builtIns() (key, kind, label, desc, labelAr, descAr) and
out_<code>.json = [{"i": row, "l": label, "d": description}]. Usage: merge_block_translations.py blocks.tsv out_fr.json fr_US"""
import json, re, sys

def key(s): return re.sub(r'[^A-Za-z0-9]+', '_', s)

def setkeys(f, kv):
    lines = open(f, encoding='utf-8').read().split('\n')
    idx = {}
    for i, l in enumerate(lines):
        m = re.match(r'^([^=#\s][^=]*?)\s*=', l)
        if m: idx[m.group(1)] = i
    add = []
    for k, v in kv.items():
        line = '%s = %s' % (k, v)
        if k in idx: lines[idx[k]] = line
        else: add.append(line)
    open(f, 'w', encoding='utf-8').write('\n'.join(lines).rstrip('\n') + '\n' + ('\n'.join(add) + '\n' if add else ''))

def main():
    tsv, js, lang = sys.argv[1:4]
    rows = [l.rstrip('\n').split('\t') for l in open(tsv, encoding='utf-8')]
    tr = {t['i']: t for t in json.load(open(js, encoding='utf-8'))}
    kv = {}
    for i, r in enumerate(rows):
        t = tr.get(i)
        if not t: continue
        k, kind = r[0], r[1]
        l = re.sub(r'\s+', ' ', t['l']).strip().replace('___', '').strip()
        d = re.sub(r'\s+', ' ', t['d']).strip()
        if kind == 'A':
            if l: kv['blk_l_' + key(k)] = l
        else:
            slots = k.count('___')
            if l: kv['blk_r_' + key(k)] = (l + ' ___' * slots).strip()
        if d: kv['blk_i_' + key(k)] = d
    setkeys('app/src/main/assets/i18n/strings_%s.properties' % lang, kv)
    print(lang, len(kv))

main()
