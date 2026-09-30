#!/usr/bin/env python3
"""Check resource completeness, substitutions and declared Android locale support."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
res=root/'app/src/main/res'
base={x.get('name'): x.text or '' for x in ET.parse(res/'values/strings.xml').getroot()}
pattern=re.compile(r'%\d+\$[,]?[sd]')
files=list(res.glob('values*/strings.xml'))
for path in files:
    entries={x.get('name'):x.text or '' for x in ET.parse(path).getroot()}
    assert entries.keys()==base.keys(),f'Missing or extra strings: {path}'
    for key,value in entries.items():
        assert value.strip(),(path,key)
        assert pattern.findall(value)==pattern.findall(base[key]),(path,key,'format placeholders')
locales={x.get('{http://schemas.android.com/apk/res/android}name') for x in ET.parse(res/'xml/locales_config.xml').getroot()}
assert locales=={'en','pt-BR','pt-PT','es','fr','it','de','ja','ko','hi','zh-Hans','zh-Hant'},locales
print(f'PASS: {len(locales)} locale variants; {len(base)} strings each; complete translations and matching format arguments.')
