# Bundled Hebrew vocabulary

Run `python3 tools/dictionary/build_dictionary.py` from the repository root to
rebuild `app/src/main/assets/hebrew.json`. The app reads the generated JSON
offline; Python is only needed when editing the dictionary.

This is an authored practical vocabulary list with approximate teaching
priorities, not a corpus-ranked frequency list. The first 100 entries are core
words and conversational phrases, the next 300 cover everyday vocabulary, and
the remaining 600 expand vocabulary and phrases. Vocabulary within a tier uses
the source order. Native-speaker review is encouraged for teaching use.

`starter.json` retains the original dictionary and stable IDs. `common.tsv`
adds to the first 100 records; `everyday.tsv` and `extended.tsv` supply the rest.
TSV rows use `stable ID|Hebrew|transliteration|English`, optionally followed by
`|feminine Hebrew|feminine transliteration`. Unpointed Hebrew can have multiple
pronunciations and meanings; those distinct words remain separate records.

The builder emits a feminine modification note from the explicitly supplied
forms. Irregular forms use the complete feminine spelling; identical spellings
note the pronunciation change. Pronunciation is supplied for each form, rather
than inferred from unpointed Hebrew.

Keep the explicit IDs in the TSV and starter file stable when editing content.
The old `khavera` and `tova`
records merge into `khaver` and `tov`. Import runs in a database transaction,
preserves existing progress, sums merged appearance counts (saturating at the
integer limit), and retains the earliest review date from introduced forms.
Aliases are then removed, so importing again cannot double-count progress.
