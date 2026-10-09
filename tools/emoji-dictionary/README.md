# Emoji search dictionary builder

`build_emoji_dict.py` creates the dictionary behind the emoji search (`emoji_<locale>.dict`) from the
Unicode CLDR emoji annotations. The default is Hungarian, so typing `kutya` or `nevet` in the emoji search
finds 🐕 and 😂.

```sh
python3 tools/emoji-dictionary/build_emoji_dict.py                # Hungarian
python3 tools/emoji-dictionary/build_emoji_dict.py --locale de    # any CLDR locale
# -> tools/emoji-dictionary/out/emoji_hu.combined and emoji_hu.dict
```

Load the `.dict` like any other dictionary (Settings → Languages & layouts → language → Dictionary; type "emoji").
Java is only needed for the last step (`--skip-dict` writes just the wordlist).

## How it works

1. Downloads the CLDR annotations of the locale (`annotations` and `annotationsDerived`, release `48.2.3`).
   Every emoji has a short name (`tts`, e.g. "arc örömkönnyekkel") and keywords (`default`).
2. Reads the list and order of the emojis from `tools/make-emoji-keys/.../ucd/17.0/emoji-test.txt`, the same file
   the emoji palette is generated from. Only fully qualified emojis are used; skin tone variants are skipped,
   the keyboard applies the preferred skin tone itself.
3. Every word of a name or keyword becomes a search word, pointing to the emoji. Ranking (`f`):
   the whole name of the emoji 22 > a keyword of its own 20 > a word of a name 18 > a word of a keyword phrase 17;
   ties are ordered like in the palette. Each word keeps at most 40 emojis.
4. Compiles the wordlist with `dicttool_aosp.jar` (same pinned tool as `tools/hu-dictionary`).

Result for Hungarian: all 1914 emojis annotated, about 4500 search words, a 113 kB dictionary.

## Licenses

| Source | License |
| --- | --- |
| [CLDR JSON](https://github.com/unicode-org/cldr-json/tree/48.2.3) annotations © 1991-2025 Unicode, Inc. | Unicode License v3 |
| `emoji-test.txt` © 2025 Unicode, Inc. (in this repository) | Unicode License v3 / Terms of Use |
| `dicttool_aosp.jar`, run locally | Apache-2.0 |

The Unicode License v3 is permissive (compatible with GPL-3.0). If you distribute the generated dictionary,
include the Unicode copyright and permission notice (see
<https://github.com/unicode-org/cldr-json/blob/48.2.3/LICENSE>) and say that the data was converted.
Unlike the emoji dictionaries from the `aosp-dictionaries` repository, no data from the Signal emoji search index
(AGPL-3.0) is used, so there are fewer slang words and synonyms but no AGPL obligations.
