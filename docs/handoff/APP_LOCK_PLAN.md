# App lock — plan (not started)

Owner's request (Italian): "aggiungi la possibilità di bloccare l'app con dati biometrici o pin di 4-6 cifre".
What was proposed to him (he did not object): Settings → **Blocco app**; PIN of 4–6 digits entered twice;
unlock with fingerprint/face if available, with the **phone's screen-lock PIN as emergency fallback** (so a forgotten
app PIN never means losing notes); "Blocca dopo": immediately / 1 min (default) / 5 / 15 min away from the app;
round keypad lock screen; 30 s wait after 5 wrong PINs; PIN stored only as a salted hash; recents preview hidden
(Android 13+).

## Dependencies (add to `gradle/libs.versions.toml` + `app/build.gradle.kts`)

- `androidx.biometric:biometric:1.1.0` (stable; minSdk 23 OK).
- `androidx.lifecycle:lifecycle-process` at the existing `lifecycle` version (2.9.2) for `ProcessLifecycleOwner`.
- Record in `docs/DECISIONS.md` (#21).

## Storage (`preferences/PreferenceRepository.kt`, DataStore, like `getCustomTheme()`)

Keys: `app_lock_enabled` (bool), `app_lock_pin` (string `pbkdf2:<iterations>:<saltB64>:<hashB64>`),
`app_lock_pin_length` (int, to auto-submit when the right number of digits is typed), `app_lock_biometric` (bool),
`app_lock_timeout` (enum preference, like the others in `PreferenceEnums.kt`: IMMEDIATELY, ONE_MINUTE default,
FIVE_MINUTES, FIFTEEN_MINUTES).

## Code (new package `org.qosp.notes.ui.lock`, unless noted)

- `PinHasher` (pure, unit-tested): digits only, length 4–6; PBKDF2WithHmacSHA256 on API 26+, fallback
  PBKDF2WithHmacSHA1 below (minSdk 24); random 16-byte salt; constant-time compare. Do not use `java.util.Base64`
  (API 26+): use `android.util.Base64` or the importer's `Base64Data` for decoding.
- `AppLock` (Koin `single`): `locked: StateFlow<Boolean>`; locked at process start when enabled;
  `ProcessLifecycleOwner` ON_STOP stores the time, ON_START locks if `now - stoppedAt >= timeout`;
  `unlockWithPin(pin)`, `unlockWithBiometric()`, failed-attempt counter → 30 s cooldown after 5.
  Inject a clock for tests. Launching the camera / file picker / share sheet sends the app to the background:
  that is why the default timeout is 1 minute (with "immediately" those flows ask for the PIN again — expected).
- Lock screen: Compose `LockScreen` (title, dots = PIN length, 3×4 round keypad 1–9 / biometric / 0 / backspace,
  error text, cooldown text). Shown by `ui/BaseActivity.kt` for **every** activity: after the content is set
  (`onPostCreate`) add a full-screen `ComposeView` to `window.decorView`, visible while `locked`, opaque, consuming
  touches; while locked an `OnBackPressedCallback` calls `moveTaskToBack(true)`. Trigger the biometric prompt in
  `onResume` when locked (once, from the resumed activity).
- Biometric: `BiometricPrompt` with `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` when
  `BiometricManager.canAuthenticate(...) == BIOMETRIC_SUCCESS` (no negative button allowed with DEVICE_CREDENTIAL;
  the keypad stays visible under the prompt for the app PIN).
- Recents: when lock is enabled call `setRecentsScreenshotEnabled(false)` (API 33+). Do not use FLAG_SECURE
  (blocks screenshots) unless the owner asks.
- Settings: new row in `res/layout/fragment_settings.xml` (a `PreferenceView` like `setting_appearance`, icon e.g. a
  lock vector), new Compose fragment `AppLockFragment` + layout (copy `AppearanceFragment` /
  `fragment_appearance.xml`), destination + action in `res/navigation/nav_graph.xml`
  (like `action_settings_to_appearance`), add to `secondaryDestinations` in `ui/MainActivity.kt`.
  Screen content: switch "Blocca l'app" (turning on → PIN setup: choose, confirm; turning off → ask the PIN first),
  "Cambia PIN", switch "Sblocca con impronta o volto" (only if available), "Blocca dopo" choice, and a short note:
  "Se dimentichi il PIN puoi sbloccare con l'impronta o il blocco schermo del telefono".
- Strings EN + IT in `strings_blackpearl.xml` files.

## Tests

- Unit: `PinHasherTest` (verify ok / wrong / lengths / non-digits / salt differs), `AppLockTest` with a fake clock
  (locks at start, timeout boundaries, cooldown after 5 failures, unlock clears).
- Device (CI): enable lock via the repository, launch `MainActivity`, check the lock overlay is shown; unlock with
  the PIN through `AppLock`, overlay gone. Remember to reset preferences in `@After` (see `CustomThemeActivityTest`).
- Screenshots: extend `scripts/capture-ui.sh` (append near the end to avoid conflicts): Settings → Blocco app →
  enable → type the PIN twice (keys have text "1".."9","0") → force-stop and relaunch → screenshot of the lock
  screen → type the PIN → screenshot unlocked.

## Known limits to tell the owner

- The home-screen widget and reminder notifications can still show note titles/content while the app is locked
  (could be a follow-up: hide widget content when locked).
- On Android 12 and older the recents preview may still show the last screen.
