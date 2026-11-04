#!/usr/bin/env python3
"""Check language coverage and Android positional formats without running a device."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent / 'app/src/main/res'
def entries(path):
    elements = list(ET.parse(path).getroot())
    result = {e.attrib['name']: e for e in elements}
    assert len(result) == len(elements), f'Duplicate resource in {path}'
    return result
def formats(text):
    return sorted(re.findall(r'%\d+\$[sd]', text or ''))
base = entries(root / 'values/strings.xml')
for language in ['ru', 'uk', 'be', 'kk', 'en']:
    path = root / f'values-{language}/strings.xml'
    localized = entries(path)
    assert localized.keys() == base.keys(), (path, localized.keys() ^ base.keys())
    for key, element in localized.items():
        reference = base[key]
        assert reference.tag == element.tag, (language, key)
        if element.tag == 'plurals':
            expected = formats(next(item for item in reference if item.get('quantity') == 'other').text)
            assert any(item.get('quantity') == 'other' for item in element), (language, key)
            for item in element:
                assert formats(item.text) == expected, (language, key, item.get('quantity'))
        else:
            assert formats(element.text) == formats(reference.text), (language, key)
            assert element.text and element.text.strip(), (language, key)
    print(f'PASS {language}: {len(localized)} resource keys, positional formats, plural fallbacks')
assert (root / 'values/strings.xml').read_bytes() == (root / 'values-ru/strings.xml').read_bytes()
print('PASS default Russian matches values-ru')
