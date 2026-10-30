#!/usr/bin/env python3
"""Every property of map.json (the panel) needs a type in types.json, and every spinner needs its options in spinners.json,
otherwise the panel shows 'field error ... NullPointerException' when a project is opened."""
import json, sys, os
a = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets')
t = json.load(open(os.path.join(a, 'types.json')))
m = json.load(open(os.path.join(a, 'map.json')))
sp = json.load(open(os.path.join(a, 'spinners.json')))
ty = set(x for v in t.values() for x in v.split(',') if x)
bad = [x for v in m.values() for x in v.split(',') if x and x not in ty]
bad += ['spinner:' + x for x in t['spinner'].split(',') if x and x not in sp]
for b in bad: print('FAIL', b)
print('ALL OK' if not bad else 'FAILED'); sys.exit(1 if bad else 0)
