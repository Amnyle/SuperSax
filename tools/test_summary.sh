#!/usr/bin/env bash
# Prints the unit-test totals from the last Gradle run.
python3 - <<'P'
import glob,re
t=f=e=s=0
for p in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    x=open(p).read(); m=re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"',x)
    t+=int(m.group(1)); s+=int(m.group(2)); f+=int(m.group(3)); e+=int(m.group(4))
    for fm in re.finditer(r'<testcase name="([^"]+)" classname="([^"]+)"[^>]*>\s*<failure message="([^"]{0,300})',x): print('FAIL', fm.group(2), fm.group(1), fm.group(3))
print(f'tests={t} failures={f} errors={e} skipped={s}')
P
