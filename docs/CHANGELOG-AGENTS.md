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

## 2026-11-10 — Second crash on "Share PDF" (branch fix/pdf-share-crash)
- Found by the emulator run of #25 (after it was merged): the Share PDF action called `requireContext()` /
  `startActivity()` inside the sheet action, where they belong to the `BottomSheet`; the sheet is closed when the PDF
  is ready → "Fragment BottomSheet not attached to a context". Moved to `EditorFragment.sharePdf()`.
- Other sheet actions were checked: they all run at once, while the sheet is still attached.
- CI: `ui-preview.yml` now fails when `logcat.txt` contains a FATAL EXCEPTION from the screenshot run.

## 2026-11-10 — App lock (branch feature/app-lock)
- New `ui/lock/*` (AppLock, PinHasher, LockScreen, AppLockFragment), overlay + biometric prompt + recents flag in
  `BaseActivity`, start + process lifecycle observer in `App`, `single { AppLock() }` in `UIModule`, settings keys in
  `PreferenceRepository`, Settings row + nav destination, strings EN/IT, 3 icons.
- Tests: `AppLockTest` (8), device `AppLockScreenTest`; screenshot script sets PIN 2580, restarts, wrong PIN, unlock.
- Not done: the widget and reminder notifications still show notes while locked (told to the owner in the screen).

## 2026-11-10 — "+" button for note options, review fixes (branch feature/editor-plus-button)
- Editor: round "+" FAB bottom-left (`fab_note_options`) opens `NoteOptionsSheet`, mirror of the edit FAB; the "⋮"
  toolbar item stays only for notes in the bin. Scroll area got 88dp bottom padding so the last lines and tags can be
  scrolled above the two buttons; edit FAB has a content description.
- Review (lint + reading): `BlackPearlColorResources.override` marked `@RequiresApi(30)` with an explicit SDK check;
  picture sheet "100 %" now stores 100 like the corner handle (was "natural size"); sheets are not shown when the
  screen state is already saved (`isStateSaved`); 4 unused size strings removed.
- Lint errors left: upstream only (MissingTranslation in `values/strings.xml`, MissingQuantity cs/ro,
  FragmentLiveDataObserve false positive).

## 2026-11-10 — Dependency updates (branch chore/dependency-updates)
- Applied Dependabot #1–#4 (GitHub Actions: setup-java 6, checkout 7, action-gh-release 3, upload-artifact 7) and
  #6–#10 (AndroidX group incl. Room 2.8.5 / Navigation 2.10.2 / Compose BOM 2026.09, coroutines 1.11, Material 1.14,
  KSP 2.3.13, OkHttp 5.5.1). Not applied: #5 (gradle/actions 6 fails: configuration-cache error in compileDebugKotlin).
- `androidx.documentfile` declared explicitly (sync with a folder used it, it was only a transitive dependency).
- Material 1.14 still has the package-private `ResourcesLoaderUtils.addResourcesLoaderToContext` used by My theme.
- Palettes regenerated with Material 1.14: only light-mode `colorOn*Container` changed (newer M3 spec, tone 30
  instead of 10); lowest container contrast 4.67 (AA). 136 unit tests, release build with R8 OK.

## 2026-11-10 — Notification icon, notes hidden outside the app when locked (branch feature/notification-icon)
- `drawable/ic_notification_icon.xml`: Black Pearl logo (from the themed launcher icon), white; the Quillpad PNGs and
  the `anydpi-v24` vector were removed (minSdk 24: one vector is enough).
- App lock on → reminder notifications say "Open Black Pearl to see it" instead of the note title; the home-screen
  widget lists no notes and explains why (`AppLock.hidesNotesOutsideTheApp()`); widgets refresh when the PIN is set
  or removed. Device test added to `AppLockScreenTest`.

## 2026-11-10 — OCR on real photos, edit button always shown (branch fix/editor-fab-and-ocr)
- Owner: "OCR fa schifo, non rileva nessun testo" on phone photos. Tesseract only applies one global threshold, so
  a page lit from one side loses whole lines. New `ui/ocr/OcrPreprocess.kt`: grey → 2× upscale when small →
  adaptive threshold (integral image, Bradley-Roth, window ≈ side/40, 12 %) → inverted when white-on-dark.
  `OcrEngine`: `user_defined_dpi=300`, PSM_AUTO then PSM_SINGLE_BLOCK fallback, decode up to 3000 px.
  Tests: `OcrEngineTest.readsAPhotoOfAPageWithShadowsAndNoise` (tilted, gradient-lit, noisy JPEG) and
  `readsWhiteTextOnADarkSign`.
- Edit/view FAB stays at the bottom right also while the note is empty (it was hidden until text was typed, which
  looked like it had gone missing next to the new "+").

## 2026-11-10 — Sample note shipped in the APK (branch feature/sample-note)
- Owner: "voglio che chiunque abbia l'apk abbia il file md come test". His notebook is now the asset
  `app/src/main/assets/sample/Quaderno delle Ricette Sburrate.md` (~17 KB); `components/SampleNote.kt` adds it once
  as a note at app start (`App.addSampleNote`), flag `sample_note_added` in the DataStore (`PreferenceRepository`).
  New installs and updates both get it; deleting it is final.
- Decision: the note is **local only** — with Nextcloud/folder sync every device would upload its own copy.
- Title = first heading ("Quaderno delle Ricette Sburrate"), text = the rest, unchanged.
- Device tests use `TestApplication`, not `App`: they do not get the note.
- Tests: `testDebugUnitTest` 139/139 (3 new in `SampleNoteTest`), `assembleDebug` OK, asset present in the APK.
  Not checked on a phone or emulator.
## 2026-11-10 — Editor looks like design 2 "Editor con immagini" (branch feature/editor-design-2)
- Owner: screen 2 of the Claude Design canvas was "non fatto"; then: shape and font must be the design's, not
  depend on Settings > Appearance ("in fondo ai temi, non va bene") → the editor always uses them.
- Header: round 48dp translucent buttons (back, note colour dot = `action_change_color`, "more" = note options,
  now always shown; pin / reminders kept), notebook as a 40dp pill. Title Fraunces SemiBold 30sp (bundled
  `res/font/fraunces_semibold.ttf`, 72pt static cut, OFL in `docs/licenses/Fraunces-OFL.txt`); body Inter 16sp,
  24dp side margins. Editor font-size preference still applies (title scaled 30/16).
- Pictures in notes are drawn with 24dp rounded corners (`ImageLayoutPlugin.RoundedImageSpan`, clipPath); the
  selected one gets a 3dp outline 3dp away. Picture bar: dark pill (inverse surface), up, down, align left, centre
  (active one light), size % (opens the picture sheet, which keeps right alignment and the size slider), remove.
- Formatting bar: floating 64dp pill (16dp margins), B, I, H, list (new `action_insert_list`, `toggleBullet`),
  picture (primary tint), scan text (`action_scan_text`, new in the bar) first; the other 12 buttons follow by
  scrolling. Edit/view and "+" FABs 64dp. Same Markdown, no DB change.
- Tests: new `ToggleBulletTest`; `./gradlew testDebugUnitTest` + `assembleDebug` (see PR). No emulator on the Mac:
  look checked only on the CI "UI preview" screenshots.
- Open: with design 2 the "+" FAB and the header "more" open the same sheet (owner may want only one).

## 2026-11-10 — Remove redundant note menus; omit the checklist widget (PR #34)
- Rebased the final file tree on current main (`277db9fe`), preserving the design-2 editor and sample note.
  The proposed checklist widget and its provider/service/test/resources are excluded at the owner's request.
- `editor_top.xml`: remove the duplicate three-dot options entry and keep the remaining actions as direct icons.
  `EditorFragment.kt`: note options use the bottom-left "+", also in the bin (restore/permanent delete stay reachable).
  `CODEMAP.md` updated. Existing pinned-notes widget unchanged.
- Validation: 143 unit tests passed; `assembleDebug` passed; XML/widget-absence checks and `git diff --check` passed.
  `lintDebug` failed with 243 pre-existing errors in unchanged files (FragmentLiveDataObserve, MissingQuantity,
  MissingTranslation); no errors in either edited application file. Lint is report-only in CI. Device checks run in CI.

## 2026-11-10 — Remove confirmed dead code
- Removed four unused Compose contrast schemes, their 140 exclusive colours, and the unused ColorFamily/
  unspecified_scheme pair; active View-derived themes and light/dark fallback schemes remain.
- Removed TagsViewModel.TagDataList and its imports, six unused sync result/exception types, the unused DAO
  methods moveRemotelyDeletedNotesToBin/getAllNoteTagRelations, and the unreferenced ic_more_vert drawable.
- No schema, migration, serialized field, sync contract or active theme change.
- Validation: 143 unit tests passed; assembleDebug and compileDebugAndroidTestKotlin passed;
  git diff --check passed. Device tests compiled but were not executed locally.

## 2026-11-10 — "Convert to list" makes real checkboxes (branch fix/convert-to-list)
- `Note.stringToTaskList()`: "- [ ] x", "[x] y", "☐/☑", bullets and numbers are stripped into the checkbox state
  instead of staying in the item text; empty lines skipped. Test `data/ConvertToListTest`.
- Checklist boxes: ticked = theme accent (`color/bp_task_checkbox`), was grey `colorControlNormal`.

## 2026-11-11 — Release from cloud sessions (manual Release workflow)
- Cloud sessions cannot push tags (git push of `refs/tags/v0.7.2` → HTTP 403), so v0.7.2 could not be released.
- `.github/workflows/release.yml`: new `workflow_dispatch` (inputs `version`, `notes`, only on `main`): GitHub
  creates the tag on the tested commit and publishes the release as latest with the given notes. Both modes now
  fail if the APK certificate SHA-256 is not the Black Pearl key. Tag-push mode unchanged (draft release).
- Owner's standing approval: publish every version that passes CI without asking (`docs/handoff/RELEASE.md`, README).
- Checks: YAML parses; the workflow itself only runs on GitHub (first real run = v0.7.2 after merge).

## 2026-11-10 — Clickable task squares, simpler formatting bar, refreshed vivid themes (scheduled run)
- Task squares: new `ui/editor/markdown/TaskBoxes.kt`. In the editor (Markdown on) `- [ ]` / `- [x]` are drawn as a
  rounded square (`TaskBoxSpan`, ReplacementSpan); ticked lines are struck through and muted. A tap on the square
  changes only the character between the brackets (note text stays plain Markdown, sync/backup unchanged).
  `TaskBoxTextWatcher` wraps Markwon's watcher (so it also runs inside `withOnlyTextWatcher<MarkwonEditorTextWatcher>`);
  span subclasses so Markwon's StrikethroughHandler does not remove them. Not done: taps in view mode (preview).
- Formatting pill: B, I, Title, bulleted list, checkbox, picture, undo, "More" (`ic_more_horiz`). "More" opens a
  BottomSheet with named actions (scan text, strikethrough, highlight, quote, link, table, code, redo when possible,
  go to top/end). Hidden menu items keep their ids; click handling moved to `EditorFragment.onFormatAction`.
  Plain-word titles `fmt_*` (EN + IT) used as tooltips.
- Themes: the 8 "Vivid" palettes now have designed light colors (tinted paper, white-ish cards, deep ink, accents
  tuned to ≥4.5:1 on paper and with white text); dark mode unchanged (generated from seed). Each has its own
  description (`theme_desc_blue` … `theme_desc_teal`, replaces `theme_desc_vivid`). Gallery card redesigned: mini
  app preview (search bar, two notes, accent "+" button), check badge and tinted card when selected.
- Palettes regenerated with `tools/palette-gen` compiled against the Material 1.14.0 color-utilities sources (the
  Google Maven host was blocked in the cloud session); night palettes came out byte-identical except comments, so
  the output matches the library.
- Tests: new `TaskBoxesTest`. No Android SDK in the cloud session: build/unit tests only via CI on the branch.

## 2026-11-11 — Linked notes and Network

Completed the editor suggestion strip and the Network destination, including graph gestures, note actions and searching. Added graph and on-device navigation tests and screenshot capture. The earlier test report showed four Robolectric initialisation failures because the app targets API 37 and the runner supports up to 36. Pinned the default JVM test SDK to 35; the app retains its target SDK.

## 2026-11-11 — Linked notes: review fixes after 0.8.0
- Review of the linked-notes code (the planned whole-branch review had not run before the merge). Fixed:
  two notes linking to each other were drawn with two edges (`LinkGraph`, one edge per pair); one stray backtick
  hid every link after it (`WikiLinks`: a code span stops at a blank line; a ``` fence is closed only by ```; 4+
  spaces before a fence = not a fence); `Note.Md` not matched; titles like "/" indexed under an empty key;
  the Network lost zoom, position and moved nodes when the keyboard opened (`GraphView.onSizeChanged`); the index
  stopped updating for good after one failed build (`LinkIndexRepository`); a `[[link]]` in a note in the bin could
  create notes; double tap opened two "Create?" dialogs; a link to the note itself stacked a second editor;
  `[[Note#Heading]]` did not scroll when the note opened in edit mode; titles containing `[ ] | #` were suggested
  after `[[` (they cannot be linked); "Linked from" cards rebuilt on every change of the note.
- Tests added for each (`WikiLinksTest`, `LinkIndexTest`, `LinkGraphTest`, `LinkSuggestionsTest`, `GraphViewTest`,
  `BacklinksViewTest`); `LinkGraphTest.demoHub` had an assertion that could not fail. Run on CI only.
- Not changed (owner to see on the phone): labels in the Network show only for nodes with 2+ links or when zoomed;
  the node sheet uses plain stacked buttons, simpler than the design canvas. Handoff README brought up to 0.8.0.

## 2026-11-11 — Network readability and navigation after 0.8.1
- Reproduced missing leaf titles and empty wiki-link pills in the emulator screenshots for the 0.8.1 tree.
- GraphView draws and hit-tests visible labels, strengthens edges and accepts a drag from a label. Tap opens an existing note by ID. Hold opens its available actions. Unresolved references offer explicit creation.
- LinkGraph keeps duplicate and untitled notes separate and the global view includes unlinked notes by default. Hidden and deleted notes remain excluded. WikiLinkPlugin measures plain labels when drawing pills.
- Added regression coverage for duplicates, untitled sources, large-graph labels, rendered pill text, and dragging then opening a note on the device. Build, lint, and emulator results are tracked on the PR.

## 2026-11-11 — Network: live dragging and neighbour highlight
- New `components/links/GraphPhysics.kt`: per-frame version of GraphLayout's forces. Start positions are the resting
  state (leftover force subtracted), so nothing moves until a node is dragged; linked notes follow, then it settles
  (~1.5 s) and stops. Off above 300 nodes (only the dragged node moves, as before).
- `GraphView`: runs it with Choreographer while a node is dragged/settling; the node under the finger (or the one whose
  sheet is open, `highlighted`) and its neighbours stay bright, their links in the theme colour, the rest dims.
- Tests: `GraphPhysicsTest` (rest, held node, neighbours follow, settles, edge cases), `GraphViewTest` (highlight,
  drag pulls the linked note). Forces tuned in a JVM prototype; Android tests run on CI.

## 2026-11-11 — Network: depth levels, tags/attachments, timelapse (Tasks 1-4)
- Task 1: `InlineTags.kt` (inline `#tag` parser); `WikiLinks` code-range helpers made `internal`.
- Task 2: `LinkGraph.kt`: `NodeKind`, `GraphNode.kind` + `attachmentType`; `LinkIndex.tagsOf` (app + inline tags merged case-insensitively, inline tags parsed once per note); `LinkGraph` depth BFS from the focus, edges among kept nodes, tag and attachment nodes.
- Task 3: `Timelapse.kt` (rank schedule, first rank precomputed per time) and `GraphView` node shapes + `playTimelapse` (needs at least 2 dated notes).
- Final review fixes: `GraphViewTest` tap TAG/ATTACHMENT and `displayTitle` tests; visible "Levels" label (`network_depth_label`) beside the slider.
- Tests added/changed: `InlineTagsTest`, `LinkGraphTest`, `GraphViewTest`. Not run locally (no Android SDK); CI.
- Task 4 details below.
- `fragment_network.xml`: chips `network_tags_chip`, `network_files_chip`; Material `Slider` `network_depth` (1..5, only visible while focused). `NetworkViewModel`: `showTags`, `showAttachments`, `depth` combined into `graph`.
- `NetworkFragment`: play item in the toolbar (`hasMenu = true`, added in `onCreateOptionsMenu`, so BaseFragment's menu invalidation cannot clear it). Every chip, Recenter, Show all, the slider and play-while-playing stop the timelapse. Tag tap/hold opens a sheet listing its notes; attachment tap opens its note, hold shows the owner note's sheet. `displayTitle` gives blank attachments a type label. Strings in EN and IT.
- Tests: not run (no Android SDK in this environment); CI to compile. Open: `NetworkNavigationTest` not touched, verify on device.

## 2026-11-12 — Inline tags: fewer false tags
- `components/links/InlineTags.kt`: `[text](#section)` anchors, `?#x` / `=#x` URL parts, HTML attributes, `![[embed|#x]]`
  are not tags; a tag needs a letter or digit and not only digits (`#-`, `#_`, `#//` are not tags); `#a/` gives `a`;
  decomposed accents stay in the name. Display-only (note text never changed).
- Tests: 6 new cases in `InlineTagsTest` (all 11 cases checked in a JVM prototype; JUnit on CI).

## 2026-11-12 — Links follow a renamed note (asks first)
- New `components/links/LinkRename.kt`: rewrites only the target of `[[Old]]`, `[[Old|label]]`, `[[Old#H]]`,
  `[[folder/Old.md]]` (label, heading, prefix, `.md`, spaces kept); skips code, embeds, notes in the bin, the renamed
  note itself, and does nothing while another note still has the old title or the new title is not linkable.
- `EditorFragment`: title remembered when its field gets focus; on leaving the field or closing the note a dialog
  "Update links?" (Update / No, not cancelable) lists how many notes; updates run in the activity scope and sync as usual.
- Owner decision: ask before changing other notes (option A).
- Tests: `LinkRenameTest` (5) — run with JUnit in a JVM prototype with a stub Note: 5/5 pass; Android build on CI.

## fix(editor): stale editor data, serialized edits, undo history, recorder/music crashes
- Files: ui/editor/EditorViewModel.kt, ui/editor/EditorFragment.kt, ui/utils/views/ExtendedEditText.kt, ui/recorder/RecorderService.kt, ui/media/MusicService.kt, ui/media/MediaActivity.kt, strings_blackpearl.xml (en, it).
- Fixes: #1 `replayExpirationMillis = 0` (no stale replay on resubscribe); #6 Mutex + latest note read inside the lock; #2 undo history cleared after `editContent`; #3 history saved as parcelable list, capped (500 entries, 100k chars saved); #4 MediaRecorder stop/prepare/start guarded; #5 audio open errors caught, toast + finish; link rename re-reads notes and re-plans on "Update".
- Tests: none added (Android-only code); no build possible here (no Android SDK). Unverified: all of it, CI must compile and test.

## 2026-11-12 — Fix review findings: lists, reminders, security
- Lists: `ActivityViewModel.update` re-reads each note by id before applying the change (stale sheet snapshot); widget
  skips hidden notes; search uses `getNonDeleted` and treats a blank query as empty.
- Reminders: `rescheduleAll` shows expired reminders instead of deleting them; `sendNotification` returns if the reminder
  row is gone or the note is missing/in the bin (`ReminderManager.shouldNotify`), and deletes the row only after `notify`
  (kept if notifications are disabled; retried at next boot).
- Security: `FLAG_SECURE` (+ `setRecentsScreenshotEnabled` on 33+) follows `AppLock.secure` at once; wrong-PIN counter and
  wait saved in DataStore (`app_lock_failures`, `app_lock_blocked_until`, wall clock); start-up read retries and fails
  closed (`loadAppLockAtStart`); Settings "current PIN" uses `AppLock.verifyPin` (same limit); new `data_extraction_rules.xml`
  and `backup_rules.xml` exclude database, datastore, media and shared prefs from cloud backup and device transfer
  (`allowBackup` unchanged; users move notes with the app's export).
- Tests: `AppLockTest` (+6), `ReminderManagerTest` (2). Not run: no Android SDK here; CI compiles and runs them.
- Not done: alarms are not cancelled at bin time (no context in the repository); the fire-time check covers it.

## 2026-11-12 — Backup/restore safety fixes (review data.md #1 #2 #3 #5 #6 #7 #9, lists.md #7)
- `BackupManager.restoreNotesFromBackup`: backup `idMappings` are no longer restored (restored notes are new local
  notes; the next sync uploads them). The DB part runs in one Room transaction (`runInTransaction`, wired in
  `di/UtilModule` with `withTransaction`, room-ktx already a dependency); notes/tags are inserted with sync
  off inside it; reminder alarms are scheduled after commit. `notesByIds()` added.
- `BackupService`: restore errors caught -> existing "restore failed" notification (cancellation rethrown); "back up
  selected notes" passes a `LongArray` of ids (`NOTE_IDS`) instead of `Note` parcelables; notes loaded in the service.
- `data/Backup.kt`: `fromString` uses `Json { ignoreUnknownKeys; coerceInputValues }`; write format unchanged.
- New `components/backup/BackupRules.kt`: zip restore accepts only `backup.json` and `media/<name>` (no dirs, sub-dirs,
  `..`, `.`, `/` or `\`, `._*`; others skipped); `uniqueName` never returns a taken name (used by
  `AttachmentHandler.IncludeFiles` and restore); `remapMediaLinks` rewrites inline
  `content://<any authority>/media_files/<name>` links in note content to the restored file.
- Tests: `BackupRulesTest` (6) and 3 new `BackupTest` cases: 10/10 pass in a JVM prototype with stubbed Room/Parcelize.
  Unverified: Android compile, `withTransaction` on device, `NoteLifecycleTest` (instrumented). Not done: process-death
  part of lists.md #7 (picker result after `notesToBackup` is lost still makes a full backup).

## 2026-11-12 — Sync review fixes (#1, #5, #7, #8, #10a, #11, #12a)
- `SynchronizeNotes.kt`: new `SyncSafetyGuard.abortReason` — MAPPING sync aborts (`SyncNotesResult.abortReason`,
  `SyncSafetyAbort` result, toast `message_sync_aborted_incomplete_listing` EN/IT) when the remote listing is empty
  while live notes are mapped, or when ≥3 and more than half of the mapped notes are missing.
- `NoteRepositoryImpl`: a note binned because its remote is gone loses its mapping (a reappearing file is imported,
  never deleted); `restoreNotes` deletes the old mapping before inserting the new one; checklist pull uses
  `Note.withRemoteListContent` (any non-task, non-blank remote line → text note with the remote content).
- `StorageBackend`: `getAll` queries the children itself (same URIs as `TreeDocumentFile`) and returns null on any
  error, missing cursor or `EXTRA_LOADING`; rename uses `DocumentsContract.renameDocument` on the mapped URI and stores
  the returned URI (old URI kept on failure).
- `ProcessRemoteActions`: mapping deleted only when the remote delete returns true; Update with no mapping creates the
  remote note (not for binned / local-only notes).
- Nextcloud: `NextcloudNote.category` nullable; updates send no category (server keeps it), create still sends "".
- Not done (owner decision): conflict copies #2, #3, #4, #6.
- Tests: `SynchronizeNotesTest` (+5, one existing test adapted to the guard), `RemoteListContentTest` (5),
  `ProcessRemoteActionsTest` (5), `NextcloudBackendTest` (+2). Guard + list logic (real `SynchronizeNotes.kt`, Note
  functions copied) 11/11 pass and Nextcloud JSON 2/2 pass in JVM prototypes; mockk/Android tests run on CI only.
  `StorageBackend` SAF code unverified (no SDK/device).

## 2026-11-12 — Fix round 2 after final review
- CI `KoinModulesTest` NPE: Koin 4.1 `verify()` casts each constructor parameter's `KType.classifier` to `KClass`, and
  kotlin-reflect returns null for a `suspend` function type, so `extraTypes` cannot fix it (checked in a JVM prototype
  with koin-test 4.1.0). `BackupManager.runInTransaction` is now a `fun interface TransactionRunner` (same lambda in
  `UtilModule`); `AppLock`'s plain `() -> Long` params pass verify as they are. Test file unchanged.
- App lock (`AppLock`, `App`, `PreferenceRepository.readAppLockState`): unreadable settings start locked only if
  `BiometricManager.canAuthenticate(LOCK_AUTHENTICATORS)` succeeds, otherwise unlocked (logged); unreadable settings
  are read again on foreground and before each PIN try; wrong-PIN counter writes are serial (Mutex + UNDISPATCHED)
  and never crash (`runCatching`). Also fixed `start()` locking from the parameter instead of the settings in use.
- Backup rules: notes excluded from Google cloud backup only, included in device-to-device transfer (Android 12+);
  Android ≤11 keeps the excludes. Recorded in DECISIONS.md.
- `EditorViewModel.update`: `CoroutineStart.UNDISPATCHED`, writes queue on the mutex in call order.
- Sync: reverted "Update with no mapping creates the remote note" (could switch the first sync from TITLE to MAPPING
  and duplicate notes); guard message names the remedy (EN/IT), shown once per app session, long toast.
- `BaseActivity`: FLAG_SECURE only below Android 13; 13+ only hides the recents preview.
- Recorder: a failed `stop()` clears `outputUri`, the dialog adds no attachment.
- `BackupRules.remapMediaLinks`: names may contain `(`, `)`, `'`; a trailing `)` closing the Markdown link is kept.
- Removed unused `first` import in App.kt.
- Tests: `AppLockTest` (+4), `BackupRulesTest` (+1): 25/25 pass in a JVM prototype (real AppLock/PinHasher/BackupRules).
  `ProcessRemoteActionsTest` (update without mapping does nothing), new `NoteRepositorySyncTest` (binned-by-sync note
  loses its mapping, no remote delete): mockk/Android, CI only. Unverified: Android compile, KoinModulesTest on CI,
  backup rules on a device.

## 2026-11-12 — Network readability and motion
- Layout linked components independently and put orphans in compact rows. A large orphan collection opens on the connected network; Recenter fits everything.
- GraphView animates opening and filter transitions, follows the system animation preference, keeps labels off other labels and node markers, and prioritizes real notes, connections and the active search. Large vaults simulate the dragged neighbourhood. Tapping a visible title or node opens its note.
- Search and filters now share a collapsible toolbar control. Timelapse nodes fade and grow when they appear.
- Added layout, crowded-label, search-hit and large-vault drag regressions. The device navigation test uses the actual rendered node position during a transition. Android verification runs in the PR Build and UI preview workflows.

## 2026-11-12 — Bible Study Kit replaces the "The Bible" demo note
- Owner request: ship the Obsidian "Bible Study Kit" (free licence; World English Bible) instead of the single demo
  note, readable by the Network with all its links. One giant note was ruled out: ~4.6 MB exceeds SQLite's ~2 MB
  cursor window and would make the editor unusable, and the Network draws one node per note.
- `components/BibleStudyKit.kt` adds the 1300 files once (local only) as notes of a "Bible Study Kit" notebook; the
  notebook id is saved first and "All notes" leaves that notebook out (owner choice A). Old `DemoLinksNote` + asset
  removed; an existing "The Bible" note on a phone stays untouched.
- `LinkParseCache` keeps each note's parsed links/tags, so a change re-reads only that note. `GraphView` tries at
  most 160 labels per frame (most linked first). Network empty-state opens "+Start here".
- Tests: `BibleStudyKitTest` (1300 files, unique titles, links seen by `LinkIndex`, added once), `LinkParseCacheTest`.
  Values checked against the files (69 links in "The Bible (WEB)", 13 notes link to Gen-01). Android build on CI.
- Checked on the files: 7336 links, all but 2 resolve to a kit note by title (aliases are not needed).
- Known limit: deleting the notebook moves its notes to "All notes".
