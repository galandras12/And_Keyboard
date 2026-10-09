# And Keyboard

An open source, offline-first Android keyboard (IME), written in Kotlin and Java.
**Version 0.1, package `com.galandras12.keyboard`, license GPL-3.0-only.**

And Keyboard is a fork of [HeliBoard](https://github.com/HeliBorg/HeliBoard) (which is based on OpenBoard and the
Android Open Source Project keyboard). The fork keeps HeliBoard's customizable keyboard, layouts, themes, clipboard,
suggestions and so on, and adds:

- **Hungarian layout** with the accented letters ö, é, á, ü on the keys (ő, ű, ó, ú, í on long press), next to
  QWERTY, QWERTZ, AZERTY and many more. A language/layout switch key is shown by default when more than one language
  or layout is enabled.
- **Glide typing without a closed source library.** HeliBoard needs a proprietary library for gesture typing; And Keyboard
  decodes gestures itself, with an open SHARK2-style path matcher (shape and location channel, frequency weighting).
  A user-supplied library is still used if present.
- **Dictionaries on demand.** No dictionaries are bundled in the APK. Pick a language in Settings → Dictionaries and download
  its dictionary, or load your own dictionary file and give it a name. A Hungarian dictionary (Hunspell hu_HU plus word
  frequencies) and a Hungarian emoji search dictionary (Unicode CLDR) are hosted in [`dictionaries/`](dictionaries/).
- **Emoji:** palette generated from the Unicode `emoji-test.txt` (categories, skin tones), emoji search with dictionaries for
  many languages including Hungarian, and a *frequently used* tab next to the recents.

## Privacy

- No keystroke logging, no analytics, no accounts.
- The `INTERNET` permission exists **only** to download dictionary files that you choose. It is used by a single class
  ([`DictionaryDownloader`](app/src/main/java/helium314/keyboard/latin/utils/DictionaryDownloader.kt)): a plain https GET, no data
  about you or your typing is sent, cleartext http is disabled. Details in [Build.md](Build.md#permissions-privacy-baseline).
- Everything else, including the glide typing decoder, runs on the device.

## Status

Early version (0.1). The layouts, decoder, emoji and dictionary code is covered by unit tests where possible; the decoder tests
use simulated finger paths, and the user interface has not been verified on many real devices yet. Bug reports are welcome.

## Build

See **[Build.md](Build.md)**: requirements, `./gradlew assembleDebug`, installing with adb, and the project structure.
Tools for the dictionaries are in [`tools/`](tools/) (`hu-dictionary`, `emoji-dictionary`, `gesture`).

## License and credits

And Keyboard is licensed under the **GNU General Public License v3.0 only** ([LICENSE](LICENSE)).

- [HeliBoard](https://github.com/HeliBorg/HeliBoard) by Helium314 and contributors, GPL-3.0
- [OpenBoard](https://github.com/openboard-team/openboard) and the [Android Open Source Project](https://source.android.com/) keyboard
  (Apache-2.0, see [LICENSE-Apache-2.0](LICENSE-Apache-2.0))
- Artwork and layout data under CC-BY-SA-4.0 ([LICENSE-CC-BY-SA-4.0](LICENSE-CC-BY-SA-4.0))
- Dictionary data: Magyar Ispell (László Németh, Ferenc Godó; MPL-2.0 or LGPL-3.0+), FrequencyWords by Hermit Dave
  (CC-BY-SA-4.0), Unicode CLDR (Unicode License v3); see [`dictionaries/README.md`](dictionaries/README.md)
- Material icons: Apache-2.0

The names, icon and store listing of HeliBoard are not part of this project; the app icon is still the one inherited from
HeliBoard and will be replaced.

---

## Röviden magyarul

Az And Keyboard egy nyílt forráskódú, elsősorban offline működő Android-billentyűzet, a HeliBoard forkja (GPL-3.0).
Magyar kiosztás (ö, é, á, ü közvetlenül a billentyűkön), swipe gépelés zárt könyvtár nélkül, emoji-panel gyakran használt
fülle és magyar keresővel, valamint letölthető szótárak (a szótárak nincsenek az APK-ban). Az INTERNET engedély kizárólag
a szótárak letöltésére szolgál, a gépelésről semmi nem hagyja el az eszközt. Fordítás és telepítés: [Build.md](Build.md).
