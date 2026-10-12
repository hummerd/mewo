"""Build the offline learning list, preserving IDs from the original app.

Tiers are approximate teaching priorities, not measured frequency ranks.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
ASSET = ROOT.parent.parent / 'app/src/main/assets/hebrew.json'


def modification(male: str, female: str) -> str:
    if male == female:
        return 'Same spelling; pronunciation changes'
    prefix = 0
    for first, second in zip(male, female):
        if first != second:
            break
        prefix += 1
    if prefix == len(male):
        return f'Add {female[prefix:]}'
    if prefix == len(female):
        return f'Remove {male[prefix:]}'
    if prefix == 0 or ' ' in male:
        return f'Use {female}'
    return f'Replace {male[prefix:]} with {female[prefix:]}'


def form(male: str, female: str, pronunciation: str) -> dict:
    return {'hebrew': female, 'transliteration': pronunciation,
            'modification': modification(male, female)}


def load_rows(name: str) -> list[dict]:
    result = []
    for line in (ROOT / name).read_text().splitlines():
        parts = line.split('|')
        assert len(parts) in (4, 6), line
        identifier, hebrew, transliteration, english = parts[:4]
        word = dict(id=identifier, hebrew=hebrew,
                    english=english, transliteration=transliteration)
        if len(parts) == 6:
            word['feminine'] = form(hebrew, parts[4], parts[5])
        result.append(word)
    return result


def build() -> None:
    starter = json.loads((ROOT / 'starter.json').read_text())
    words = [word for word in starter if word['id'] not in ('khavera', 'tova')]
    forms = {'khaver': ('חברה', 'khavera', 'Friend'),
             'tov': ('טובה', 'tova', 'Good'),
             'gadol': ('גדולה', 'gdola', 'Big'),
             'katan': ('קטנה', 'ktana', 'Small')}
    for word in words:
        if word['id'] in forms:
            female, pronunciation, meaning = forms[word['id']]
            word['english'] = meaning
            word['feminine'] = form(word['hebrew'], female, pronunciation)
        if word['id'] in ('khaver', 'tov'):
            word['mergedIds'] = ['khavera' if word['id'] == 'khaver' else 'tova']
    words += load_rows('common.tsv')
    assert len(words) == 100, len(words)
    words += load_rows('everyday.tsv') + load_rows('extended.tsv')
    assert len(words) == 1000, len(words)
    assert len({word['id'] for word in words}) == len(words)
    assert len({(word['hebrew'], word['transliteration']) for word in words}) == len(words)
    for index, word in enumerate(words):
        word['priority'] = 100 if index < 100 else 300 if index < 400 else 1000
    ASSET.write_text(json.dumps(words, ensure_ascii=False, indent=2) + '\n')
    print('Wrote 1000 records: 100 core, 300 everyday, 600 extended.')


if __name__ == '__main__':
    build()
