#!/usr/bin/env python3
"""Inspect the trainer's XLSX plan without executing its formulas."""
import argparse
import json
import re
import zipfile
import xml.etree.ElementTree as ET

NS = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main',
      'r': 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'}
REL = '{http://schemas.openxmlformats.org/package/2006/relationships}'


def read_workbook(path):
    with zipfile.ZipFile(path) as archive:
        strings = []
        if 'xl/sharedStrings.xml' in archive.namelist():
            strings = [''.join(e.itertext()) for e in ET.fromstring(archive.read('xl/sharedStrings.xml'))]
        workbook = ET.fromstring(archive.read('xl/workbook.xml'))
        rels = ET.fromstring(archive.read('xl/_rels/workbook.xml.rels'))
        targets = {r.get('Id'): r.get('Target') for r in rels.findall(REL + 'Relationship')}
        result = []
        for sheet in workbook.findall('s:sheets/s:sheet', NS):
            if not re.search(r'plan', sheet.get('name', ''), re.I) or sheet.get('state') == 'hidden':
                continue
            target = targets[sheet.get('{%s}id' % NS['r'])].lstrip('/')
            if not target.startswith('xl/'):
                target = 'xl/' + target
            root = ET.fromstring(archive.read(target))
            cells = {}
            for cell in root.findall('.//s:sheetData/s:row/s:c', NS):
                value = cell.find('s:v', NS)
                inline = cell.find('s:is', NS)
                text = value.text if value is not None and value.text else ''.join(inline.itertext()) if inline is not None else ''
                if cell.get('t') == 's' and text:
                    text = strings[int(text)]
                cells[cell.get('r')] = text
            for merged in root.findall('s:mergeCells/s:mergeCell', NS):
                m = re.fullmatch(r'([A-Z]+)(\d+):\1(\d+)', merged.get('ref', ''))
                if m:
                    col, start, end = m.group(1), int(m.group(2)), int(m.group(3))
                    value = cells.get(f'{col}{start}', '')
                    for row in range(start + 1, end + 1):
                        if not cells.get(f'{col}{row}'):
                            cells[f'{col}{row}'] = value
            days = []
            day = None
            maxrow = max((int(re.search(r'\d+', ref).group()) for ref in cells), default=0)
            for row in range(15, maxrow + 1):
                label = cells.get(f'A{row}', '')
                if re.match(r'^D[IÍ]A\s*\d+', label, re.I):
                    day = {'name': label.strip(), 'exercises': []}
                    days.append(day)
                name = cells.get(f'I{row}', '').strip()
                code = cells.get(f'H{row}', '').strip()
                if day is None or not name or not re.match(r'^[A-Z]\d+\.?$', code, re.I):
                    continue
                weeks = []
                for index in range(5):
                    cols = [chr(ord('K') + 4*index + offset) if ord('K')+4*index+offset <= ord('Z') else 'A'+chr(ord('A') + ord('K')+4*index+offset-ord('Z')-1) for offset in range(4)]
                    weeks.append(dict(zip(('sets', 'reps', 'load', 'effort'), (cells.get(f'{col}{row}', '') for col in cols))))
                day['exercises'].append({'code': code, 'name': name, 'weeks': weeks})
            result.append({'name': sheet.get('name'), 'days': days})
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('workbook')
    args = parser.parse_args()
    print(json.dumps(read_workbook(args.workbook), ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
