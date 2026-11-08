# Architecture

Inherited from Quillpad; Black Pearl keeps it unchanged unless `docs/DECISIONS.md` says otherwise.

## Build

- Single module `:app`. Gradle 9.5 (wrapper), Android Gradle Plugin 9.x, Kotlin 2.2, KSP.
- Version catalog: `gradle/libs.versions.toml`. Build script: `app/build.gradle.kts`.
- `compileSdk`/`targetSdk` 37, `minSdk` 24, Java/Kotlin target 17, core library desugaring on.
- Kotlin package / Android `namespace`: `org.qosp.notes` (inherited). `applicationId`: `io.github.mancio24x.blackpearl`.

## Layers

```
UI (Fragments + ViewBinding, some Compose) ──> ViewModels ──> Repositories ──> Room DAOs ──> SQLite
                                                     │
                                                     └──> Sync core ──> Nextcloud API (Retrofit) | Local folder (SAF)
```

- **Pattern:** MVVM. Fragments observe `Flow`s exposed by ViewModels. Single Activity (`MainActivity`) + Navigation
  component (`nav_graph.xml`), plus `LauncherActivity` (Compose) and `MediaActivity`.
- **DI:** Koin (`di/*Module.kt`), with KSP annotations config check.
- **Database:** Room, `AppDatabase` version 5, migrations 1→5 in the same file; schemas exported to `app/schemas/`.
  Tables: `notes`, `notebooks`, `tags`, `note_tags`, `reminders`, `cloud_ids` (id mappings for sync).
  Lists (`taskList`, `attachments`) are stored as JSON columns via `DatabaseConverters`.
- **Note content:** plain text / Markdown string in `notes.content`. Checklist items live in `taskList` (not in content).
  Attachments are a JSON list of `Attachment(type, path, description, fileName)`; files live in the app's private
  media folder and are exposed with `FileProvider` (`${applicationId}.provider`).
- **Markdown:** Markwon (core, tables, strikethrough, task lists, images via Coil plugin).
- **Images/media:** Coil 2 (images, GIF, video frames), ExoPlayer (audio/video).
- **Background work:** WorkManager (`SyncWorker`, `BinCleaningWorker`), foreground services (backup, recorder, music).
- **Preferences:** DataStore (`preferences/`), encrypted prefs (security-crypto) for Nextcloud credentials.
- **Sync:** `SynchronizeNotes` compares local notes with a backend (`ISyncBackend`): Nextcloud Notes REST API or a
  local folder of `.md`/`.txt` files. **Only title + content sync; attachments, colors, checklists-as-taskList do not.**
- **Backup:** zip file = `backup.json` (serialized `Backup`: notes, notebooks, tags, reminders, joins, id mappings)
  + `media/` folder with attachment files. Restore merges into the DB.
- **Crash reports:** ACRA (mail sender + dialog, opt-in by user).

## Key libraries

Room, Koin, Kotlin Coroutines/Flow, kotlinx.serialization, Markwon, Coil, ExoPlayer, Retrofit + OkHttp, WorkManager,
DataStore, Material Components, Jetpack Compose (Material 3) for a few screens, LeakCanary (debug only).
Tests: JUnit4, MockK, Robolectric, AndroidX Test, Room testing.
