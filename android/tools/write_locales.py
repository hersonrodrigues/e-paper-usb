#!/usr/bin/env python3
"""Build Android string resources from the reviewed UTF-8 locale catalogs."""
from pathlib import Path
from xml.sax.saxutils import escape
ROOT = Path(__file__).resolve().parents[1]
FOLDERS = {'en': 'values', 'pt-BR': 'values-pt-rBR', 'pt-PT': 'values-pt', 'es': 'values-es', 'fr': 'values-fr', 'it': 'values-it', 'de': 'values-de', 'ko': 'values-ko', 'ja': 'values-ja', 'hi': 'values-hi', 'zh-Hans': 'values-b+zh+Hans', 'zh-Hant': 'values-b+zh+Hant', 'ar': 'values-ar', 'bn': 'values-bn', 'ru': 'values-ru', 'id': 'values-in', 'tr': 'values-tr', 'vi': 'values-vi', 'th': 'values-th', 'ur': 'values-ur', 'fa': 'values-fa', 'pl': 'values-pl', 'nl': 'values-nl', 'uk': 'values-uk', 'ta': 'values-ta'}
def read_catalog(path):
    result = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        if not line or line.startswith('#'): continue
        key, value = line.split('=', 1)
        assert key not in result, (path, key)
        result[key] = value
    return result
def render_resources(catalog):
    content = '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
    for key, value in catalog.items():
        value = escape(value).replace("'", "\\'").replace('"', '\\"')
        content += f'    <string name="{key}">{value}</string>\n'
    content += '</resources>\n'
    return content

if __name__ == '__main__':
    base = read_catalog(ROOT/'tools/locales/en.txt')
    for locale, folder in FOLDERS.items():
        catalog = read_catalog(ROOT/f'tools/locales/{locale}.txt')
        assert catalog.keys() == base.keys(), (locale, base.keys()-catalog.keys(), catalog.keys()-base.keys())
        content = render_resources(catalog)
        dest = ROOT/f'app/src/main/res/{folder}'
        dest.mkdir(exist_ok=True)
        (dest/'strings.xml').write_text(content, encoding='utf-8')
        # Make English an explicit match in ordered multi-language system preferences.
        if locale == 'en':
            dest = ROOT/'app/src/main/res/values-en'; dest.mkdir(exist_ok=True)
            (dest/'strings.xml').write_text(content, encoding='utf-8')
    config = '<?xml version="1.0" encoding="utf-8"?>\n<locale-config xmlns:android="http://schemas.android.com/apk/res/android">\n'
    config += ''.join(f'    <locale android:name="{tag}" />\n' for tag in FOLDERS)
    (ROOT/'app/src/main/res/xml/locales_config.xml').write_text(config+'</locale-config>\n', encoding='utf-8')
    print(f'{len(FOLDERS)} locale variants, {len(base)} strings each')
