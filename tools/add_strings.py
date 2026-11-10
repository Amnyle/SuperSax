#!/usr/bin/env python3
"""Adds strings to values/strings.xml and values-fr/strings.xml in sync.

Input on stdin, one per line: key|English|French   (lines starting with # are ignored).
Existing keys are replaced. Apostrophes, quotes, & and < are escaped for Android resources.
"""
import re, sys, os
root = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res')
files = {'en': os.path.join(root, 'values', 'strings.xml'), 'fr': os.path.join(root, 'values-fr', 'strings.xml')}

def esc(s):
    s = s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    s = s.replace("'", "\\'").replace('"', '\\"')
    if s.startswith('@') or s.startswith('?'): s = '\\' + s
    return s

def load(path):
    if not os.path.exists(path): return []
    txt = open(path, encoding='utf-8').read()
    return re.findall(r'(<(?:string|plurals)[^>]*name="([^"]+)"[^>]*>.*?</(?:string|plurals)>)', txt, re.S)

entries = [l.rstrip('\n') for l in sys.stdin if l.strip() and not l.startswith('#')]
for lang, idx in (('en', 1), ('fr', 2)):
    existing = load(files[lang])
    order = [k for _, k in existing]; body = {k: x for x, k in existing}
    for e in entries:
        parts = e.split('|')
        if len(parts) != 3: sys.exit('bad line: ' + e)
        key = parts[0].strip(); val = parts[idx]
        if key not in body: order.append(key)
        fmt = ' formatted="false"' if val.count('%') > 1 and '$' not in val else ''
        body[key] = f'<string name="{key}"{fmt}>{esc(val)}</string>'
    with open(files[lang], 'w', encoding='utf-8') as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
        for k in order: f.write('    ' + body[k] + '\n')
        f.write('</resources>\n')
print('ok', len(entries))
