# Agent work log

Append one short entry per significant task: files, decisions, tests (with result), open issues.

## 2026-11-08 — Project initialization (Claude Code)
- Files: rebranding (`app/build.gradle.kts`, `settings.gradle`, `no_translate_strings.xml`, `xml-v25/shortcuts.xml`,
  `nav_graph.xml`, `ActivityResultUtils.kt`), launcher icons, `graphics/black-pearl/`, docs, `.github/workflows/*`,
  `.github/dependabot.yml`; removed Infigraph configs and upstream workflows.
- Decisions: see DECISIONS.md #1–9.
- Tests: local `assembleDebug` + `testDebugUnitTest` OK (75 tests, 0 failures); APK badging verified
  (package io.github.mancio24x.blackpearl, label "Black Pearl"); CI Build on main green. Instrumented tests not run.
- Release signing key generated and stored outside the repo (owner's Mac); GitHub secrets set.
- Open: fastlane metadata and notification icon still Quillpad's; Dependabot PRs #1–#10 to review.

## 2026-11-08 — Material 3 UI + customization (branch feature/material3-ui, PR #11)
- Files: `values*/themes.xml`, generated `values*/palettes.xml`, `styles.xml`, `styles_blackpearl.xml`, `attrs_blackpearl.xml`,
  `strings_blackpearl.xml` (en, it), layouts (M3 text appearances, outline variant, note card shape), `PreferenceEnums.kt`,
  `AppPreferences.kt`, `PreferenceRepository.kt`, `BaseActivity.kt`, `ui/theme/Theme.kt`, `ui/settings/*`, `tools/palette-gen/*`,
  `scripts/capture-ui.sh`, `.github/workflows/ui-preview.yml`.
- Fix: `showPreferenceDialog` mapped clicks to the unfiltered enum list (wrong choice when an option is unsupported).
- Tests: `assembleDebug` OK; `testDebugUnitTest` 78 tests, 0 failures (new `AppearancePreferencesTest`). Not yet checked on a device.
- Open: review UI preview screenshots; manual check on phone; note colors (feature 2) still use the old fixed tints.

## 2026-11-08 — Feature 1: repositionable images (branch feature/image-layout)
- Files: `ui/editor/markdown/ImageBlocks.kt`, `ImageLayoutPlugin.kt`, `di/MarkwonModule.kt`, `ui/editor/EditorFragment.kt`,
  `values/ids_blackpearl.xml`, `strings_blackpearl.xml` (en, it), tests `ImageBlocksTest`, `ImageLayoutPersistenceTest`.
- Data safety: edits update both the EditText (editor source of truth) and the saved note.
- Tests: `testDebugUnitTest` 90 tests, 0 failures; `assembleDebug` OK. Not yet checked on a device.
- Open: drag & drop, text wrap beside images (see DECISIONS #13).

## 2026-11-08 — Feature 2: note colors (branch feature/note-colors)
- Files: `ui/common/NoteColorDialog.kt`, `EditorFragment.kt` (picker), `AbstractNotesFragment.kt` + `menu/main_selected_notes.xml`
  (multi-select), `ActivityViewModel.colorNotes`, `values-night/themes.xml` (Cyan), strings, tests `NoteColorContrastTest`,
  `NoteColorPersistenceTest`.
- Tests: `testDebugUnitTest` 94 tests, 0 failures; `assembleDebug` OK. Not yet checked on a device.

## 2026-11-09 — Feature 3: offline OCR (branch feature/offline-ocr)
- Files: `ui/ocr/*`, `EditorFragment.kt`, `menu/editor_top.xml`, `xml/file_provider_paths.xml` (cache ocr/), `assets/tessdata/*`,
  `gradle/libs.versions.toml`, `app/build.gradle.kts` (dep, noCompress, release ABIs, proguard file), `app/proguard-rules.pro`,
  strings, tests `OcrTextTest` (JVM), `OcrEngineTest` (device, CI emulator job).
- Tests: `testDebugUnitTest` 99 tests, 0 failures; `assembleDebug` + `assembleRelease` OK (release 25 MB, Tesseract names kept by R8).
- Open: OcrEngineTest result in CI; phone check.

## 2026-11-09 — Feature 4: PDF export (branch feature/pdf-export)
- Files: `ui/pdf/*`, `EditorFragment.kt`, `menu/editor_top.xml`, `xml/file_provider_paths.xml` (cache pdf/), strings,
  tests `PdfLayoutTest` (JVM), `NotePdfExporterTest` (device), `ui-preview.yml` runs device tests.
- Tests: `testDebugUnitTest` 103 tests, 0 failures; `assembleDebug` OK. Device tests: see CI.

## 2026-11-09 — Release 0.2.0 branch
- #11 was squash-merged; #12–#15 were stacked on the pre-squash commits and conflicted with main. The 4 feature
  commits were rebased onto main (content unchanged, verified by diff) and opened as one PR with version 0.2.0
  (versionCode 4). Local: 103 unit tests OK, assembleDebug + assembleRelease OK.
- Lesson: with squash merges, do not stack PRs; branch each feature from main after the previous one is merged.

## 2026-11-09 — Themes at Atlante level (branch feature/themes-atlante)
- Design reference: Claude Design canvas "Black Pearl — Redesign" (screens 3–5).
- Files: `ui/theme/ThemeRoles.java`, `CustomTheme.kt`, generated `CustomThemeResources.kt`, `colors_custom_theme.xml`,
  `palettes.xml`; `tools/palette-gen/*`; `ui/appearance/*`; `fragment_appearance.xml`, nav graph, `MainActivity`;
  `PreferenceEnums.kt` (ThemeGroup, new themes, Pill, FabSize), `PreferenceRepository.kt`, `BaseActivity.kt`,
  `SettingsFragment.kt` + layout (one "Appearance" entry), `MainFragment.kt` (FAB size); removed `AppearanceDialogs.kt`.
- Tests: 107 unit tests OK (new `ThemeRolesTest`; contrast test aware of always-dark themes); `assembleDebug` OK.
- Open: device check of runtime "My theme"; notes list redesign (1A/1B) waiting for owner's choice.

## 2026-11-09 — QA pass (branch qa/test-and-cleanup)
- Bugs fixed: editor content source when the user emptied the text (could resurrect deleted text on image/OCR insert);
  image menu could target a non-standalone copy of the same image; "]" in image captions broke Markdown; OCR progress
  dialog leaked if the screen closed; Romanian (99% translated) was stripped from the APK and ar/ro were missing from
  the per-app language list (fa/sv stay excluded: ~10% translated); Appearance previews read Configuration via LocalContext.
- Cleanup: 250 unused strings (all locales), 8 unused drawables, unused colors.xml; Black Pearl strings marked
  English+Italian only (MissingTranslation).
- Tests: new device test `NoteLifecycleTest`; CI now runs ALL device tests. Unit tests 108 OK.
- Lint: FragmentLiveDataObserve in ExtendedListAdapter is a false positive (observes viewLifecycleOwnerLiveData).

## 2026-11-09 — Import from other apps (branch feature/import-other-apps)
- New package `components/importer/` (pure Kotlin parsers + `OtherAppsImport` Android writer), `BackupService`
  `Action.IMPORT` (foreground, notification + toast), `ImportNotesContract`, drawer item "Import and export" (import,
  export backup, restore backup) in `MainActivity`/`nav_drawer.xml`, strings EN/IT, jsoup dependency.
- Tests: `NoteImporterTest` (15 tests) on sample exports of Keep (zip and tgz), Simplenote, Standard Notes (incl. Super
  notes and an encrypted item), Fossify, Notally/Omni Notes JSON, Evernote .enex, Joplin .jex, Obsidian vault, TXT in
  Windows-1252/UTF-16, HTML; device test `ImportOtherAppsTest` (real zip → DB, pictures copied, existing notes kept,
  Black Pearl backup restored).
- Open: samples are written from the apps' documented formats, not exported from the real apps; check on the phone.
## 2026-11-09 — Notes list "Soft" (1A) + theme fixes (branch feature/notes-list-soft)
- 1A: `layout_appbar_main.xml` (serif title, round menu button, search pill, notebook chips), `fragment_main.xml`
  (floating quick-action pill instead of the BottomAppBar; `main_bottom.xml` and its style removed), `MainFragment.kt`,
  card padding 16dp / gap 12dp, inset rounded images (`NoteViewHolder`), extra rounded = default card shape.
- Fixes: "My theme" showed Material You colors (overlay) and went blank right after Apply; any theme forcing the other
  light/dark mode lost its palette at app start (night mode now set in attachBaseContext).
- Tests: `AppearancePreferencesTest` updated; device tests `CustomThemeTest.appliedThemeShowsTheChosenColors`,
  `CustomThemeActivityTest` (real activity, light and dark My theme, cold start and recreate). Screenshot script
  creates sample notes and a notebook, saves theme logs.

## 2026-11-10 — Simpler M3 menus (branch feature/editor-menus)
- Editor: `editor_top.xml` keeps mode / pin / reminders / restore + "more" → `NoteOptionsSheet` (Compose); actions moved
  to `ids_blackpearl.xml`; `onOptionsItemSelected` → `runNoteAction(id)`, switches via `setNoteOption`. Export sheet =
  PDF save/share + note backup. Picture sheet = segmented alignment / size + move + remove.
- Lists: `showMenuForNote` → `NoteListSheet`. `BottomSheet.kt` restyled (drag handle, no flat background, pill rows);
  popup menus rounded (`Widget.BlackPearl.PopupMenu`). Strings only used by the old menus removed (all locales);
  Italian "Rispristina" typo fixed.
- Tests: unit tests and all 36 device tests pass; screenshots of the sheets in the UI preview run.

## 2026-11-10 — Move / resize pictures by hand (branch feature/photo-handles)
- New `ImageHandlesView` (overlay in `fragment_editor.xml` next to the preview) + `ImageGestures` (pure: rendered
  non-blank lines ↔ source lines for moves, `moveBy`, `snapPercent`); `EditorFragment.setupImageLayout` wires it.
- `ImageLayoutPlugin`: a picture between line breaks is standalone (was: only alone in its paragraph), so pictures
  inserted between two text lines can be tapped.
- Tests: `ImageGesturesTest` (4); all unit tests and 36 device tests pass; screenshot script creates a note with a web
  picture, selects it, resizes it with the corner handle and drags it below the last line.
- Open: line↔unit mapping can be off around fenced code blocks and tables (moves still go the right way).

## 2026-11-10 — Crash on "Save PDF" / "Share PDF" (branch fix/pdf-export-crash)
- Cause: `BottomSheet` kept its actions (lambdas) in the fragment arguments; when the app went to the background with
  a sheet open (file picker, share sheet, camera, home) Android saved them → "Parcelable encountered IOException
  writing serializable object" → crash. Affected Export, Scan text, attachment, reminder, tag and notebook sheets.
- Fix: actions kept in memory, only the header in the arguments; a sheet recreated by Android closes itself.
- Tests: `BottomSheetStateTest` (Robolectric; reproduced the crash before the fix), 128 unit tests OK; the screenshot
  run now goes through Export → Save PDF and → Share PDF.
