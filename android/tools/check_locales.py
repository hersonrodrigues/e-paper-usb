#!/usr/bin/env python3
"""Check resource completeness, substitutions and declared Android locale support."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from write_locales import FOLDERS, read_catalog, render_resources
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
assert locales==set(FOLDERS),locales
assert {p.stem for p in (root/'tools/locales').glob('*.txt')}==set(FOLDERS),'Catalog registration mismatch'
assert {p.parent.name for p in files}==set(FOLDERS.values())|{'values-en'},'Resource folder mismatch'
for tag,folder in FOLDERS.items():
    catalog=read_catalog(root/f'tools/locales/{tag}.txt')
    assert catalog.keys()==base.keys(),tag
    assert (res/folder/'strings.xml').read_text(encoding='utf-8')==render_resources(catalog),f'Regenerate {tag}'
gradle=(root/'app/build.gradle').read_text()
filters=re.search(r'resourceConfigurations\s*\+=\s*\[([^]]+)\]',gradle).group(1)
assert set(re.findall(r"'([^']+)'",filters))=={'en'}|{f.removeprefix('values-') for f in FOLDERS.values() if f!='values'},'Gradle language filters mismatch'
print(f'PASS: {len(locales)} locale variants; {len(base)} strings each; complete translations and matching format arguments.')
