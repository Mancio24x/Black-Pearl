# Environment, CI and gotchas

## Local (owner's Mac, Claude Code)

- `export ANDROID_HOME=$HOME/Library/Android/sdk`; build: `./gradlew -q assembleDebug`, unit tests:
  `./gradlew -q testDebugUnitTest`, device-test compile check: `./gradlew -q compileDebugAndroidTestKotlin`,
  release/R8 check: `./gradlew -q assembleRelease` (unsigned locally, fine for checking R8).
- Gradle and git writes needed the Bash sandbox disabled in this setup.
- **No emulator on the Mac**: every device check runs in CI (below).
- Shell is zsh: arrays are 1-indexed; `echo ======` breaks (`=` expansion) — quote it.
- Reading unit-test XML results with Python: `ElementTree` elements without children are falsy — use
  `x = tc.find('failure'); if x is None: ...`, not `or`.
- The editing tool may turn ` `-style escapes written in Kotlin source into the literal invisible character.
  After writing such code, re-escape (a tiny Python replace) or check with `grep $'\xc2\xa0'`.

## CI (`.github/workflows/`)

- `build.yml`: unit tests, lint (report only), debug APK artifact `black-pearl-debug-apk`.
- `ui-preview.yml`: Android 15 emulator; runs **all device tests** (`connectedDebugAndroidTest`, fails the job if any
  fail) then `scripts/capture-ui.sh` (screenshots + `logcat.txt` with theme logs and `AndroidRuntime` errors; the
  script itself never fails, so read the screenshots and the log). Runs on PRs touching UI / androidTest / the script,
  or by hand: `gh workflow run ui-preview.yml --repo Mancio24x/Black-Pearl --ref <branch>`.
  Artifacts: `device-test-reports` (HTML; counts in `connected/debug/index.html`) and `ui-screenshots`.
  Download: `gh run download <id> --repo Mancio24x/Black-Pearl -D <dir>`.
- `scripts/capture-ui.sh` creates sample notes, a notebook "Scuola" and a note "Foto" with a web picture, then shoots
  the list, drawer, settings, appearance, My theme, editor, note options sheet, Export → Save/Share PDF (after
  #25), picture selection / resize / move, long press. It types with `adb shell input text` (spaces as `%s`) and
  taps by visible text/content-description (`tap_text`).
- `release.yml`: see `RELEASE.md`.

## GitHub

- Repo `Mancio24x/Black-Pearl`; remote `upstream` = Quillpad (fetch only, push disabled). `gh` without `--repo`
  resolves to **upstream** — always pass `--repo Mancio24x/Black-Pearl`.
- Ruleset "bello" on `main`: linear history, no direct updates → PRs are squash-merged **by the owner**.
  `gh pr merge --admin` and force-push were refused by the safety classifier: do not try.

## Code gotchas learned the hard way

- `ColorResourcesOverride.applyIfPossible` adds Material You's overlay; My theme uses `BlackPearlColorResources`.
- Night mode must be set in `BaseActivity.attachBaseContext` (setting it in onCreate makes AppCompat re-apply the
  manifest theme and lose the palette).
- Never put lambdas or other non-saveable objects in fragment arguments (crash when the app goes to the background).
  Use `OptionsSheet` (Compose) or the fixed `BottomSheet`, which keep actions in memory.
- Robolectric tests cannot load app resources here (turning on `isIncludeAndroidResources` breaks other tests:
  Koin started twice, targetSdk 37 > Robolectric's 36). Test pure logic or Bundles/Parcels directly.
- Device tests share one database and the media folder: clean up what you create and compare counts, never assume
  empty (`MediaStorageManagerTest` counts media files).
