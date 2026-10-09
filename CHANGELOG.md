# Changelog

All versions are **pre-releases** (early, not fully tested on real devices). Newest version first.

## 0.3 - 2026-10-10 (pre-release)

### Added
- **Setup message** at the top of the main settings screen, looking like an error message: shown while And Keyboard is not turned on in
  the system settings (tapping it opens the keyboard settings) or is not the keyboard in use (tapping it opens the keyboard picker).
  It is checked again every time the settings are shown.
- Settings → Dictionaries shows the **list of installed dictionaries** (downloaded or loaded from a file) with name, language, kind
  and size, and a button to remove each.
- Glide typing diagnostics in the gesture settings: whether gestures are handled (dictionary loaded, switched on, allowed in the field),
  how many words the decoder knows, how many gestures were started and decoded, the last result and the last problem. This shows
  why a gesture does nothing, if it does not work on a device.

### Changed
- Version 0.3 (versionCode 3).

## 0.2 - 2026-10-09 (pre-release)

### Added
- New logo: a keyboard with an ampersand.
- Dictionaries are downloaded **automatically** for every enabled language that has none (stable dictionaries only, plain https
  download, on by default, can be switched off in Settings → Dictionaries; failed attempts are retried every 6 hours at most).
- Themes **Full White** (white keyboard, black letters) and **AMOLED Black** (black keyboard, white letters).
- Hungarian layout: long press on **ö** gives **ő**, long press on **ü** gives **ű**.
- Glide typing status card in the gesture settings: which decoder is used and whether the dictionary it needs is installed.
- About screen: the original source application (HeliBoard) and the project page on GitHub.
- Tests: the glide typing decoder is tested on the keys of real keyboards, through the whole suggestion pipeline, and with
  a real dictionary file (needs the host-built native library, see `tools/host-native`).

### Fixed
- Glide typing did not work without the closed source library: the main dictionary of a language is a dictionary collection
  that could not provide its word list to the open decoder, so no suggestions were ever produced.

### Changed
- Version 0.2 (versionCode 2).
- Android backup is switched off (`allowBackup=false`), so learned words and the clipboard history are not copied to cloud backups.
- Very large word lists (millions of words) are capped for the glide typing decoder (the rarest words are left out).
- Debug builds are signed with a fixed, publicly known key, so that each build can update the previous one.

## 0.1 - 2026-10-09 (pre-release)

First version, a fork of HeliBoard 4.2-beta1 (upstream commit `bc2b911`), renamed **And Keyboard**, package `com.galandras12.keyboard`.

### Added
- Hungarian QWERTZ layout with ö, é, á, ü on the keys; language and layout switch key shown by default.
- Open source glide typing decoder (SHARK2 style path matching with frequency weighting), used when the closed source library is
  not present; tests on simulated finger paths.
- Emoji: *frequently used* tab, and a script that builds a Hungarian emoji search dictionary from the Unicode CLDR annotations.
- Dictionary tools: Hungarian dictionary built from Hunspell hu_HU and the FrequencyWords list (`tools/hu-dictionary`).
- Dictionaries are no longer bundled in the APK: download per language in Settings → Dictionaries, or load your own file and name it;
  a notice on the main screen when an enabled language has no dictionary.
- `INTERNET` permission, used only to download dictionary files (one class, https only, size limit, SHA-256 for files hosted by this project).
- README for And Keyboard, Build.md (build, install, structure, privacy baseline), hosted Hungarian dictionaries in `dictionaries/`.

### Changed
- App name, package and project links; the HeliBoard wiki and community links stay as references.

## Source application

And Keyboard is a fork of **[HeliBoard](https://github.com/HeliBorg/HeliBoard)** by Helium314 and contributors (GPL-3.0), which is
based on **OpenBoard** and the **Android Open Source Project** keyboard. And Keyboard on GitHub:
<https://github.com/galandras12/And_Keyboard>.
