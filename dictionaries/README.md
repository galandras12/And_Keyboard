# Dictionaries hosted here

These files are downloaded by the keyboard on request (Settings → Dictionaries → Download), they are not part of the APK.
They are built with the scripts in `tools/` and committed as they are; the checksums are listed in
`app/src/main/java/helium314/keyboard/latin/utils/DictionaryCatalog.kt`.

| File | Built with | Data sources and licenses |
| --- | --- | --- |
| `main_hu.dict` | `tools/hu-dictionary/build_hu_dict.py` | Hungarian Hunspell dictionary hu_HU 1.9, © 2025 László Németh and Ferenc Godó, MPL-2.0 or LGPL-3.0-or-later (used under LGPL-3.0-or-later); word frequencies from [FrequencyWords](https://github.com/hermitdave/FrequencyWords) (Hermit Dave, based on OpenSubtitles), **CC-BY-SA-4.0**. Changes: words expanded, filtered and ranked by frequency, converted to the AOSP dictionary format. |
| `emoji_hu.dict` | `tools/emoji-dictionary/build_emoji_dict.py` | Unicode CLDR 48.2.3 emoji annotations, © 1991-2025 Unicode, Inc., Unicode License v3 (<https://github.com/unicode-org/cldr-json/blob/48.2.3/LICENSE>). Changes: converted to the AOSP dictionary format. |

The data in these files is distributed under **CC-BY-SA-4.0** (the share-alike condition of the frequency list
applies to `main_hu.dict`; `emoji_hu.dict` stays under the Unicode License v3). See `tools/hu-dictionary/README.md`
and `tools/emoji-dictionary/README.md` for details.
