# Testing & build

Requirements: JDK 17+ (CI uses 21), Android SDK (platform 37 is auto-installed by Gradle if licenses are accepted).
Create `local.properties` with `sdk.dir=/path/to/Android/sdk` (never commit it).

## Commands

| Goal | Command |
|---|---|
| Compile only (fast check) | `./gradlew compileDebugKotlin` |
| Debug APK | `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` |
| All unit tests (JVM + Robolectric) | `./gradlew testDebugUnitTest` |
| One test class | `./gradlew testDebugUnitTest --tests 'org.qosp.notes.data.BackupTest'` |
| Lint (static checks) | `./gradlew lintDebug` → `app/build/reports/lint-results-debug.html` |
| Instrumented tests (emulator/device connected) | `./gradlew connectedDebugAndroidTest` |
| DB migration tests only | `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.qosp.notes.data.DatabaseMigrationTest` |
| Install on a connected phone | `./gradlew installDebug` |
| All device tests (emulator/device) | `./gradlew connectedDebugAndroidTest` (CI: "UI preview" runs them all) |
| UI screenshots (emulator/device running) | `./gradlew assembleDebug -PTESTLAB_BUILD=true && scripts/capture-ui.sh` → `ui-shots/` (CI: workflow "UI preview") |
| Release APK (signed) | see "Release signing" |

CI (`.github/workflows/build.yml`) runs: `testDebugUnitTest`, `lintDebug` (report only), `assembleDebug`.
All instrumented tests run on an API 35 emulator in the "UI preview" workflow (PRs touching app code), together with
the screenshots. Run them locally too before merging changes to database, backup, reminders or media storage.

## Diagnosing failures

- Read the first `e:` / `FAILURE:` lines: `./gradlew assembleDebug 2>&1 | rg -m5 "^e:|FAILURE|What went wrong" -A3`.
- Test failures: `app/build/reports/tests/testDebugUnitTest/index.html` (in CI: artifact `reports`).
- Dependency problems: `./gradlew :app:dependencies --configuration debugRuntimeClasspath | rg <lib>`.
- Stale state: `./gradlew clean` (the local `build-cache/` folder can also be deleted).
- Device logs: `adb logcat --pid=$(adb shell pidof -s io.github.mancio24x.blackpearl)`.

## Release signing

1. Create a keystore once (keep it forever, with a backup outside GitHub):
   `keytool -genkeypair -v -keystore black-pearl-release.jks -alias blackpearl -keyalg RSA -keysize 4096 -validity 10000`
2. GitHub → repo → Settings → Secrets and variables → Actions → add:
   `KEYSTORE_BASE64` (`base64 -i black-pearl-release.jks | pbcopy`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
3. Tag `main`: `git tag -a v0.2.1 -m "Black Pearl 0.2.1" && git push origin v0.2.1`. Version comes from the tag
   (`versionCode = X*10000 + Y*100 + Z`), no file to edit. The Release workflow creates a **draft** release; publish it manually.

**If the keystore is lost**, no future APK can update installed copies: users would have to uninstall
(losing local notes unless they made a backup) and install a new app. Keep two offline copies of the keystore and passwords.

## Test coverage map (Phase 7)

| Area | Existing tests | Status |
|---|---|---|
| Backup/restore | `test/.../data/BackupTest.kt` | Existing |
| DB migrations 1→5 | `androidTest/.../data/DatabaseMigrationTest.kt` | Existing (device) |
| Sync | `test/.../data/sync/**` | Existing |
| Attachments | `test/.../ui/attachments/AttachmentUtilsTest.kt`, `androidTest/.../MediaStorageManagerTest.kt` | Existing |
| Reminders | `androidTest/.../tests/reminders/*` | Existing (device) |
| Note create/edit/delete, checklists, bin, empty-note discard, backup/restore | `androidTest/.../tests/NoteLifecycleTest.kt` | Device |
| Colors persistence | — | To add (feature 2) |
| Image positioning | — | To add (feature 1) |
| OCR | — | To add (feature 3) |
| PDF export | — | To add (feature 4) |
