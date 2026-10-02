"""Read the generated OOXML with standard-library tools, independently of the writer."""
from pathlib import Path
from zipfile import ZipFile
import sys
import xml.etree.ElementTree as ET

NS = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
checks = 0

def check(value, message):
    global checks
    checks += 1
    assert value, message

def cells(xml):
    root = ET.fromstring(xml)
    result = {}
    for cell in root.findall('.//s:c', NS):
        if cell.attrib.get('t') == 'inlineStr':
            result[cell.attrib['r']] = ''.join(cell.itertext())
        else:
            result[cell.attrib['r']] = float(cell.findtext('s:v', '0', NS))
        check(cell.find('s:f', NS) is None, 'user text must not become a formula')
    return result

for name, summed, occupied in [('work.xlsx', 6, 5), ('all.xlsx', 10, 7), ('empty.xlsx', 0, 0), ('selected.xlsx', 4, 4)]:
    path = Path(sys.argv[1]) / name
    with ZipFile(path) as archive:
        check(archive.testzip() is None, 'ZIP integrity')
        for member in archive.namelist():
            if member.endswith('.xml') or member.endswith('.rels'):
                ET.fromstring(archive.read(member))
        sheets = [cells(archive.read(f'xl/worksheets/sheet{i}.xml')) for i in range(1, 7)]
        overview, timeline, days, groups, tasks, intervals = sheets
        check(abs(overview['B5'] * 24 - summed) < 1e-9, name + ' sum')
        check(abs(overview['B6'] * 24 - occupied) < 1e-9, name + ' union')
        check(abs(sum(days[f'C{i}'] for i in range(5, 12)) * 24 - summed) < 1e-9, 'day reconciliation')
        check(abs(sum(days[f'D{i}'] for i in range(5, 12)) * 24 - occupied) < 1e-9, 'day union reconciliation')
        duration = sum(value for key, value in intervals.items() if key.startswith('E') and key[1:].isdigit() and int(key[1:]) >= 5)
        check(abs(duration * 24 - summed) < 1e-9, 'raw interval reconciliation')
        if name == 'work.xlsx':
            other = [int(key[1:]) for key, value in timeline.items() if key.startswith('B') and value == 'Другое']
            check(len(other) == 1, 'private activities collapse into one lane per day')
            check(abs(timeline[f'AA{other[0]}'] * 24 - 3) < 1e-9, 'overlapping private activity union')
            check(sum(value for key, value in timeline.items() if key[:-len(str(other[0]))] in ('O', 'P', 'Q') and key.endswith(str(other[0]))) == 180, 'private minutes')
            joined = ''.join(archive.read(m).decode() for m in archive.namelist())
            check('SECRET' not in joined and 'HIDDEN_NOTE' not in joined, 'no private names in any package part')
        if name == 'empty.xlsx':
            check(overview['B7'] == 0, 'future week has no untracked elapsed time')
            check(len([v for v in timeline.values() if v == 'Впереди']) == 7, 'seven future days')
print(f'PASS: {checks} independent OOXML checks')
