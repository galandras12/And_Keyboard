# Build.md

This project is a fork of [HeliBoard](https://github.com/Helium314/HeliBoard)
(upstream commit `bc2b911`, version `4.2-beta1`), licensed under GPL-3.0
(see `LICENSE`).

## Requirements

| Component        | Version used                          |
|------------------|---------------------------------------|
| JDK              | 17+ (tested with 21)                  |
| Gradle           | 9.7.1 (via `./gradlew`, auto-downloaded) |
| Android Gradle Plugin | 9.1.1                            |
| Kotlin           | 2.4.0                                 |
| Android SDK      | platform `android-37.0`, build-tools `37.0.0`, platform-tools |
| Android NDK      | `28.0.13004108` (the dictionary engine is native C++) |
| minSdk / target / compileSdk | 21 / 37 / 37              |

## Build

```sh
# 1. Point Gradle at the Android SDK (local.properties is git-ignored)
echo "sdk.dir=/path/to/android-sdk" > local.properties   # or export ANDROID_HOME

# 2. Install the SDK pieces (once), using cmdline-tools
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0" "ndk;28.0.13004108"

# 3. Build the debug APK
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/HeliBoard_4.2-beta1-debug.apk`
(application id `helium314.keyboard.debug`, so it installs next to a release build).
The first build takes ~6 minutes (dependencies + NDK compilation for 4 ABIs).

Notes:
- If Maven answers `429 Too Many Requests` (rate limiting on shared networks), rerun, or use
  `./gradlew assembleDebug --max-workers=1 --no-parallel -Dorg.gradle.internal.repository.max.tentatives=8 -Dorg.gradle.internal.repository.initial.backoff=3000`.
- With the configuration cache enabled, a failed dependency download aborts the run; rerunning continues from the Gradle cache.
- The `debug` build type prints a harmless warning (debuggable + minify enabled).

## Install and run

Physical phone (USB debugging enabled) or an emulator with a running `adb` device:

```sh
adb devices
adb install -r app/build/outputs/apk/debug/HeliBoard_4.2-beta1-debug.apk
# enable and select the keyboard
adb shell ime enable helium314.keyboard.debug/helium314.keyboard.latin.LatinIME
adb shell ime set    helium314.keyboard.debug/helium314.keyboard.latin.LatinIME
```

Or enable it manually: Settings → System → Languages & input → On-screen keyboard →
Manage keyboards → turn on the debug keyboard, then pick it from the keyboard switcher.

Emulator example (needs hardware virtualization, i.e. KVM on Linux / HAXM / Hypervisor):

```sh
sdkmanager "emulator" "system-images;android-35;google_apis;x86_64"
avdmanager create avd -n kbd -k "system-images;android-35;google_apis;x86_64"
emulator -avd kbd
```

Status of this environment: the cloud sandbox used for phase 1 has no `/dev/kvm`, so the
APK was built but not installed on an emulator there. Install it on a phone or a local emulator.

## Project structure

```
build.gradle.kts, settings.gradle   Root Gradle config; modules: :app, :tools:make-emoji-keys
gradle/                             Gradle wrapper
app/
  build.gradle.kts                  Android config, dependencies, NDK (ndkBuild) setup
  src/main/
    AndroidManifest.xml             Declares the IME service, settings activities, permissions
    java/helium314/keyboard/
      latin/                        IME core: LatinIME service, input logic, suggestions,
                                    dictionaries, clipboard, contacts (Kotlin + Java)
      keyboard/                     Keyboard view, key/layout parsing, rendering, popups
      settings/                     Compose-based settings UI
      event/, accessibility/,
      compat/, dictionarypack/      Event handling, accessibility, API compat, dictionary pack
    jni/                            Native C++ dictionary / proximity code (built via ndkBuild)
    res/, assets/                   Resources, translations, keyboard layouts (no dictionaries, see below)
  src/debug, src/debugNoMinify      Variant-specific resources and overrides
  src/test                          Unit tests
tools/                              Helper scripts (emoji keys, diacritics, release, hu-dictionary, emoji-dictionary, gesture)
dictionaries/                       Dictionaries hosted for the in-app download (not part of the APK)
fastlane/, art/                     Store metadata and artwork
layouts.md                          Documentation of the custom layout format
```

## Permissions (privacy baseline)

The built APK declares: `INTERNET`, `READ_USER_DICTIONARY`, `WRITE_USER_DICTIONARY`, `READ_CONTACTS`,
`VIBRATE`, `RECEIVE_BOOT_COMPLETED` (all but `INTERNET` inherited from HeliBoard). Project rules:
**no keystroke logging**, and **`INTERNET` is only for downloading dictionary files**.

- `INTERNET` is used by exactly one class, `latin/utils/DictionaryDownloader.kt`: a plain https GET of a dictionary
  file that the user picked in Settings → Dictionaries (or from the "dictionary missing" notice). Nothing is
  sent about the user, the device or the typed text; cleartext http is disabled in the manifest and refused by the
  downloader (also for redirects), the size is limited to 80 MB, and files hosted by this project are checked
  against a SHA-256 (see `DictionaryCatalog.kt`).
- No dictionaries are bundled in the APK. They are downloaded on request, or loaded from a file with a name
  chosen by the user. Hosted dictionaries and their licenses: `dictionaries/README.md`.
- `READ_CONTACTS` (used for contact-name suggestions) is upstream behavior; whether to keep it is still open.

## Upstream

`git remote add upstream https://github.com/Helium314/heliboard` — used to pull upstream
changes. Third-party licenses: GPL-3.0 (`LICENSE`), Apache-2.0 (`LICENSE-Apache-2.0`, AOSP-derived
code), CC-BY-SA-4.0 (`LICENSE-CC-BY-SA-4.0`, artwork/layout data).
