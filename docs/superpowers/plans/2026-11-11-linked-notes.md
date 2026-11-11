# Linked notes and Network view — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Obsidian-compatible `[[links]]` (pills, backlinks, `[[` suggestions) and a draggable "Network" graph behind one drawer item, plus the demo note "The Bible".

**Architecture:** Pure Kotlin logic in `org.qosp.notes.components.links` (parser, index, graph, layout) fed by `NoteRepository.getNonDeleted()`; one Koin singleton keeps the current `LinkIndex`. UI: a Markwon plugin turns links into pill spans in the editor preview, thin hooks in `EditorFragment` (backlinks, suggestions, heading scroll), and a new `NetworkFragment` with a custom `GraphView`. No Room, backup or sync change.

**Tech Stack:** Kotlin, Views + ViewBinding, Markwon 4, Koin, Material 3 components, JUnit4 + Robolectric (unit), Espresso (device, CI).

**Spec:** `docs/superpowers/specs/2026-11-11-linked-notes-design.md` · Design: https://claude.ai/artifact/QNAmdLWMrpWjKYuYC5Ap48

## Global Constraints

- No Room schema change, no change to `Backup.kt` / `BackupManager` / sync code. Stored note text is never rewritten by rendering.
- Link syntax exactly Obsidian's: `[[Target]]`, `[[Target|label]]`, `[[Target#Heading]]`, `[[Target#Heading|label]]`; `![[...]]` left as text.
- Matching: title trimmed, case-insensitive, trailing `.md` ignored, path prefix `a/b/Note` → `Note`. Duplicates → most recently modified.
- Only non-deleted, non-hidden notes take part (archived included). A target in the bin is a ghost.
- No new library (F-Droid). Material 3 look as in the design canvas; colors from theme attributes, never hardcoded.
- New strings only in `values/strings_blackpearl.xml` (English) + `values-it/strings_blackpearl.xml` (Italian).
- Kotlin package `org.qosp.notes` (do not rename). Don't touch app id.
- Local Gradle cannot run in cloud sessions (toolchain download blocked, no SDK): **verification = push to the PR branch and read CI** (`build`: unit tests + lint + assembleDebug; `preview`: device tests + screenshots). On a machine with Gradle use the commands given.
- `EditorFragment.kt` is ~1300 lines: add logic in new files; only thin hooks there.

## Review Focus

1. A note with hundreds of links or a vault of ~2000 notes: index rebuild must stay off the main thread and be debounced; Network layout must not freeze the UI → `LinkIndexTest.buildsTwoThousandNotesQuickly`, `GraphLayoutTest.layoutOfThreeHundredNodesFinishes`.
2. `[[` inside inline code / fenced code / an unclosed `[[` → stays plain text (no pill, no index entry) → `WikiLinksTest`.
3. Renaming a note title or emptying it → links to the old title become ghosts, no crash; empty title is never a target → `LinkIndexTest.blankTitleIsNeverATarget`.
4. Tapping a ghost pill twice / a note created meanwhile with that title → opens the existing note, never a duplicate → `LinkActionsTest.createOrOpenOpensExisting`.
5. The demo note must not be added twice (fresh install, update, after delete) and must stay local-only → `DemoLinksNoteTest`.

---

### Task 1: Wiki link parser

**Files:**
- Create: `app/src/main/java/org/qosp/notes/components/links/WikiLinks.kt`
- Test: `app/src/test/java/org/qosp/notes/components/links/WikiLinksTest.kt`

**Interfaces:**
- Produces: `data class WikiLink(val target: String, val heading: String?, val label: String?, val range: IntRange)` with `val shown: String` (= `label?.trim()?.ifBlank { null } ?: target`); `object WikiLinks { fun find(markdown: String): List<WikiLink>; fun key(title: String): String }` — `key` = normalized match key (trim, drop trailing `.md`, last `/` segment, lowercase via `Locale.ROOT`).

- [ ] **Step 1: Write failing tests** — `WikiLinksTest`:
  - `findsPlainAliasHeading`: `"a [[Genesis]] b [[010 Faith MOC|Faith MOC ]] c [[Note#Part|x]]"` → targets `["Genesis","010 Faith MOC","Note"]`, labels `[null,"Faith MOC ","x"]`, heading of 3rd `"Part"`, `shown` of 2nd `"Faith MOC"`, `range` of 1st = `2..12`.
  - `skipsEmbedsCodeAndUnclosed`: `"![[pic.png]] `[[a]]` ```\n[[b]]\n``` [[c"` → empty list.
  - `keyNormalizes`: `key(" Folder/Genesis.md ") == "genesis"`, `key("GENESIS") == "genesis"`.
  - `demoFileHas69Links`: reads `docs/superpowers/specs/linked-notes/The Bible.md` via `File("../docs/...")` → `find(...).size == 69`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*WikiLinksTest*'` (CI) → FAIL (unresolved reference).
- [ ] **Step 3: Implement** `WikiLinks.find` — regex `(?<!!)\[\[([^\[\]|#\n]+)(?:#([^\[\]|\n]*))?(?:\|([^\[\]\n]*))?]]`; first compute excluded ranges for fenced blocks (```` ``` ```` / `~~~` lines) and inline code spans (backtick runs) and drop matches inside them. Blank target → skip.
- [ ] **Step 4: Run** same command → PASS.
- [ ] **Step 5: Commit** `feat(links): Obsidian wiki link parser`

### Task 2: Link index and graph model

**Files:**
- Create: `app/src/main/java/org/qosp/notes/components/links/LinkIndex.kt`, `.../links/LinkGraph.kt`
- Test: `app/src/test/java/org/qosp/notes/components/links/LinkIndexTest.kt`, `LinkGraphTest.kt`

**Interfaces:**
- Consumes: `WikiLinks.find`, `WikiLinks.key`.
- Produces:
  - `class LinkIndex(notes: List<Note>)`: `fun resolve(target: String): Note?`; `fun outgoing(noteId: Long): List<WikiLink>`; `fun backlinks(noteId: Long): List<Backlink>`; `fun ghostTargets(): Set<String>` (display spelling of first occurrence); `fun ghostCitedBy(target: String): List<Note>`; `val titles: List<Note>` (participating notes, most recently modified first); `fun isGhost(target: String): Boolean`.
  - `data class Backlink(val from: Note, val line: String)` (`line` = trimmed source line containing the link).
  - `data class GraphNode(val key: String, val title: String, val noteId: Long?, val degree: Int)` (`noteId == null` = ghost); `data class GraphEdge(val from: String, val to: String)`.
  - `object LinkGraph { fun build(index: LinkIndex, showUnlinked: Boolean = false, focusKey: String? = null): Pair<List<GraphNode>, List<GraphEdge>> }` — `focusKey` = local graph depth 1; self-links produce no edge; duplicate edges collapsed.

- [ ] **Step 1: Write failing tests** (build `Note(id=…, title=…, content=…, modifiedDate=…)`):
  - `LinkIndexTest.backlinksAndGhosts`: A=`"see [[b]] and [[Missing]]"`, B=`"x"` → `resolve("B")==B`, `backlinks(B.id).single().line=="see [[b]] and [[Missing]]"`, `ghostTargets()==setOf("Missing")`.
  - `duplicateTitlesPickNewest`, `binAndHiddenExcluded` (link to a deleted note → ghost; hidden note not in `titles`), `blankTitleIsNeverATarget`, `buildsTwoThousandNotesQuickly` (2000 notes × 5 links < 1000 ms).
  - `LinkGraphTest.demoHub`: demo note + `Genesis` note linking back → 70 nodes, hub degree 69, `Genesis.noteId != null`, others ghost; `unlinkedHiddenUnlessAsked`; `focusShowsNeighboursOnly`; `selfLinkNoEdge`.
- [ ] **Step 2: Run** `--tests '*LinkIndexTest*' --tests '*LinkGraphTest*'` → FAIL.
- [ ] **Step 3: Implement** both (maps keyed by `WikiLinks.key`; participating = `!isDeleted && !isHidden && title.isNotBlank()`).
- [ ] **Step 4: Run** → PASS. **Step 5: Commit** `feat(links): link index and graph model`

### Task 3: Graph layout

**Files:** Create `app/src/main/java/org/qosp/notes/components/links/GraphLayout.kt`; Test `.../links/GraphLayoutTest.kt`

**Interfaces:**
- Produces: `object GraphLayout { fun layout(nodes: List<GraphNode>, edges: List<GraphEdge>, iterations: Int = 300, seed: Long = 42): Map<String, PointF2> }` with `data class PointF2(val x: Float, val y: Float)` (plain, no android.graphics, so JVM-testable). Coordinates centered on (0,0).

- [ ] **Step 1: Failing tests:** `deterministic` (two runs equal), `noNaN`, `nodesSeparated` (every pair ≥ 8 units apart for the demo graph), `hubNearCenter` (|hub| < 40), `layoutOfThreeHundredNodesFinishes` (< 1500 ms).
- [ ] **Step 2: Run** `--tests '*GraphLayoutTest*'` → FAIL.
- [ ] **Step 3: Implement** Fruchterman–Reingold: initial positions on a seeded circle, repulsion `k²/d`, spring `d²/k` on edges, linear cooling, `k = 40`; clamp distance ≥ 0.01.
- [ ] **Step 4: Run** → PASS. **Step 5: Commit** `feat(links): force-directed graph layout`

### Task 4: Import keeps Obsidian links

**Files:** Modify `app/src/main/java/org/qosp/notes/components/importer/TextFormats.kt` (`MarkdownLinks.resolve`, the `wikiLink.replace` line ~96); Test: `app/src/test/java/org/qosp/notes/components/importer/NoteImporterTest.kt:213-216`.

- [ ] **Step 1: Change the expectation** in `obsidianVaultZip` to `"Firenze e i Medici.\n\n![mappa.png](bp-import:0)\n\nVedi [[Diario|il diario]] e ![schema](bp-import:1)\n\nsource: libro di testo"`.
- [ ] **Step 2: Run** `--tests '*NoteImporterTest*'` → FAIL on that assertion.
- [ ] **Step 3: Remove** the `wikiLink` replacement (and the now unused regex). Embeds `![[…]]` keep today's handling.
- [ ] **Step 4: Run** → PASS (all importer tests). **Step 5: Commit** `fix(import): keep Obsidian [[links]] instead of flattening them`

### Task 5: Demo note "The Bible"

**Files:**
- Create: `app/src/main/assets/sample/The Bible.md` (copy of `docs/superpowers/specs/linked-notes/The Bible.md`, byte-identical); `app/src/main/java/org/qosp/notes/components/DemoLinksNote.kt`
- Modify: `preferences/PreferenceRepository.kt` (key `DEMO_LINKS_NOTE_ADDED = booleanPreferencesKey("demo_links_note_added")`, `isDemoLinksNoteAdded()`, `setDemoLinksNoteAdded()` like the sample note ones at :149-156); `App.kt:109-112` (call `DemoLinksNote.addOnce` after `SampleNote.addOnce` in the same coroutine)
- Test: `app/src/test/java/org/qosp/notes/components/DemoLinksNoteTest.kt`

**Interfaces:** Produces `object DemoLinksNote { const val ASSET = "sample/The Bible.md"; fun parse(markdown: String): Note; suspend fun addOnce(context: Context, notes: NoteRepository, preferences: PreferenceRepository) }`.

- [ ] **Step 1: Failing tests:** `parse` → `title == "The Bible"`, content starts with `"links: [[010 Faith MOC|Faith MOC ]]"`, has no `"# The Bible"` line, `isLocalOnly`, `WikiLinks.find(content).size == 69`, CRLF input gives same result; `addOnce` twice with a fake repo/prefs (pattern of `SampleNoteTest`) inserts once.
- [ ] **Step 2: Run** `--tests '*DemoLinksNoteTest*'` → FAIL.
- [ ] **Step 3: Implement** — title fixed `"The Bible"`; content = file with `\r\n`→`\n` and the first line equal to `# The Bible` removed (other lines untouched); insert with `sync = false`.
- [ ] **Step 4: Run** → PASS. **Step 5: Commit** `feat(links): demo note The Bible shipped in the APK`

### Task 6: Links in the note preview (pills, open, create, heading)

**Files:**
- Create: `app/src/main/java/org/qosp/notes/components/links/LinkIndexRepository.kt`, `app/src/main/java/org/qosp/notes/ui/editor/markdown/WikiLinkPlugin.kt`, `app/src/main/java/org/qosp/notes/ui/links/LinkActions.kt`
- Modify: `di/MarkwonModule.kt` (add plugin before `TaskListPlugin`; `LinkResolverDef` replaced by a resolver delegating `blackpearl-link:` URLs to `WikiLinkPlugin.onClick`); the Koin module that provides repositories (add `single { LinkIndexRepository(get(), CoroutineScope(Dispatchers.Default)) }`); `EditorFragment.kt` (set click listener tag on `textViewContentPreview`; read new nav arg); `res/navigation/nav_graph.xml` (`fragment_editor` gets `<argument android:name="scrollToHeading" app:argType="string" android:defaultValue=""/>`); `values/ids.xml` (`wiki_link_listener`)
- Test: `app/src/test/java/org/qosp/notes/ui/editor/markdown/WikiLinkPluginTest.kt` (Robolectric), `app/src/test/java/org/qosp/notes/ui/links/LinkActionsTest.kt`

**Interfaces:**
- Consumes: `LinkIndex`, `WikiLinks`.
- Produces:
  - `class LinkIndexRepository(notes: NoteRepository, scope: CoroutineScope) { val index: StateFlow<LinkIndex> }` — `getNonDeleted()` → `debounce(300)` → build on `Dispatchers.Default`.
  - `WikiLinkPlugin(index: () -> LinkIndex)`: `processMarkdown` rewrites each `WikiLink` (via `WikiLinks.find`, so code is skipped) to `[shown](blackpearl-link:<urlencoded target>#<urlencoded heading>)`; `afterSetText` replaces those `LinkSpan`s with `WikiPillSpan(ghost: Boolean)` (ReplacementSpan: rounded rect, `colorPrimaryContainer`/`colorOnPrimaryContainer`, ghost = dashed outline `colorOutline`) plus a `ClickableSpan`; a pill wider than the text view is drawn ellipsized. PDF export (`NotePdfExporter`) keeps the plain label (no pill). `fun interface WikiLinkClick { fun onWikiLink(target: String, heading: String?) }` read from view tag `R.id.wiki_link_listener`.
  - `object LinkActions { suspend fun createOrOpen(title: String, notebookId: Long?, repo: NoteRepository, index: LinkIndex): Long }` — returns existing id when `index.resolve(title)` finds one, else inserts `Note(title = title, notebookId = notebookId)` and returns the new id.
  - Editor navigation to a note: `findNavController().navigate(R.id.fragment_editor, bundleOf("transitionName" to "editor_$id", "noteId" to id, "scrollToHeading" to (heading ?: "")))`.
- [ ] **Step 1: Failing tests:** `WikiLinkPluginTest.rewritesOutsideCodeOnly` (`processMarkdown("x [[A|b]] `[[c]]`")` == `"x [b](blackpearl-link:A#) `[[c]]`"`), `ghostPillForMissing` (render `"[[Nope]]"` with an empty index into a TextView → one `WikiPillSpan` with `ghost == true`), `clickCallsListener` (tag listener, invoke ClickableSpan → `("A", "Part")` for `[[A#Part]]`). `LinkActionsTest.createOrOpenOpensExisting` (index contains "Exodus" → no insert, returns its id), `createOrOpenInserts` (fake repo records insert with given notebookId).
- [ ] **Step 2: Run** `--tests '*WikiLinkPluginTest*' --tests '*LinkActionsTest*'` → FAIL.
- [ ] **Step 3: Implement** plugin, repository, actions; in `EditorFragment` set the listener: existing → navigate; ghost → `MaterialAlertDialogBuilder` title `link_create_title` (`Create «%1$s»?` / `Creare «%1$s»?`), message `link_create_message`, positive `link_create_open` → `LinkActions.createOrOpen(target, data.note?.notebookId, …)` then navigate. After the preview text is set, if `args.scrollToHeading` is not blank, find the first heading line equal (case-insensitive) in `content` and `scrollView.smoothScrollTo(0, layout.getLineTop(line))`.
- [ ] **Step 4: Run** → PASS; CI `build` green. **Step 5: Commit** `feat(links): tappable [[link]] pills in notes, create missing notes`

### Task 7: "Linked from" section

**Files:** Create `app/src/main/java/org/qosp/notes/ui/links/BacklinksView.kt`, `res/layout/item_backlink.xml`; Modify `res/layout/fragment_editor.xml` (inside `linear_layout`, after `text_view_content_preview`'s container and before `text_view_date`: `<org.qosp.notes.ui.links.BacklinksView android:id="@+id/backlinks" … android:visibility="gone"/>`), `EditorFragment.kt` (collect `LinkIndexRepository.index` with the note id → `binding.backlinks.show(list) { open(it.from.id) }`, hidden in edit mode and for list notes); Test `app/src/test/java/org/qosp/notes/ui/links/BacklinksViewTest.kt` (Robolectric).

**Interfaces:** `class BacklinksView(context, attrs) : LinearLayout { fun show(items: List<Backlink>, onClick: (Backlink) -> Unit) }` — gone when empty; header `link_backlinks_header` (`Linked from · %1$d` / `Collegata da · %1$d`), one M3 card per item (title, line in `?attr/colorOnSurfaceVariant`, 24dp corners as the design).

- [ ] **Step 1: Failing tests:** `goneWhenEmpty`, `showsHeaderCountAndRows` (2 items → header text "Linked from · 2", 2 child cards), `clickReturnsItem`.
- [ ] **Step 2: Run** `--tests '*BacklinksViewTest*'` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS. **Step 5: Commit** `feat(links): Linked from section under the note`

### Task 8: `[[` suggestions while writing

**Files:** Create `app/src/main/java/org/qosp/notes/ui/links/LinkSuggestions.kt` (pure), `app/src/main/java/org/qosp/notes/ui/links/LinkSuggestionStrip.kt` (view), `res/layout/item_link_suggestion.xml`; Modify `res/layout/fragment_editor.xml` (strip next to `container_bottom_toolbar`, same position, `gone`), `EditorFragment.kt` (TextWatcher on `editTextContent`: when `LinkSuggestions.query` ≠ null show strip and hide `container_bottom_toolbar`; restore on close); Test `app/src/test/java/org/qosp/notes/ui/links/LinkSuggestionsTest.kt`.

**Interfaces:**
- `object LinkSuggestions { fun query(text: CharSequence, cursor: Int): String?; fun suggest(query: String, index: LinkIndex, selfId: Long?, max: Int = 8): List<Suggestion>; fun insert(text: String, cursor: Int, title: String): Pair<String, Int> }`
- `data class Suggestion(val title: String, val kind: Kind) { enum class Kind { NOTE, GHOST, CREATE } }`
- `query` = text between the last unclosed `[[` on the current line and the cursor (null if `]]`, a newline, or no `[[` in between). `suggest`: notes (excluding `selfId`) then ghosts; prefix matches first, then contains; case-insensitive; cap `max - 1`, last = `CREATE(query)` when query not blank and no exact match. `insert` replaces `[[query` with `[[title]]`, cursor after `]]`.

- [ ] **Step 1: Failing tests:** `queryDetection` (`"Next: [[Ex|"` → `"Ex"`; `"[[a]] b|"` → null; `"[[a\nb|"` → null), `suggestOrder` (notes "Exodus notes","Viaggi in Egitto", ghost "Exodus", "Ezekiel", query "ex" → `["Exodus notes"(NOTE), "Exodus"(GHOST), "Viaggi in Egitto"(NOTE), CREATE "ex"]`), `insertCompletes` (`insert("Next: [[Ex", 10, "Exodus")` == `"Next: [[Exodus]]" to 16`).
- [ ] **Step 2: Run** `--tests '*LinkSuggestionsTest*'` → FAIL. **Step 3: Implement** pure part, then strip (horizontal list of M3 pills inside a 64dp rounded surface like the formatting pill; dot style per kind as in design screen 8; label `link_suggest_create` = `Create «%1$s»` / `Crea «%1$s»`). **Step 4: Run** → PASS. **Step 5: Commit** `feat(links): [[ suggestions above the keyboard`

### Task 9: Network screen

**Files:**
- Create: `app/src/main/java/org/qosp/notes/ui/links/NetworkFragment.kt`, `NetworkViewModel.kt`, `GraphView.kt`, `NodeSheet.kt`; `res/layout/fragment_network.xml`, `res/layout/sheet_network_node.xml`; `res/drawable/ic_network.xml` (three connected dots, 24dp, as design screen 1)
- Modify: `res/menu/nav_drawer.xml` (item `@+id/fragment_network`, icon `ic_network`, title `@string/nav_network` in group `section_notes` after `fragment_deleted`); `res/navigation/nav_graph.xml` (fragment `fragment_network`, layout `fragment_network`); `ui/MainActivity.kt:77-82` (add `R.id.fragment_network` to `primaryDestinations`); the Koin module that declares view models (`viewModel { NetworkViewModel(get()) }`)
- Test: `app/src/test/java/org/qosp/notes/ui/links/NetworkViewModelTest.kt`, `app/src/test/java/org/qosp/notes/ui/links/GraphViewTest.kt` (Robolectric), device `app/src/androidTest/java/org/qosp/notes/ui/NetworkTest.kt`

**Interfaces:**
- Consumes: `LinkIndexRepository.index`, `LinkGraph.build`, `GraphLayout.layout`, `LinkActions.createOrOpen`.
- Produces: `data class NetworkState(val nodes: List<GraphNode>, val edges: List<GraphEdge>, val positions: Map<String, PointF2>, val query: String, val showUnlinked: Boolean, val focusKey: String?)`; `NetworkViewModel.state: StateFlow<NetworkState>`; `fun setQuery(q: String)`, `fun toggleUnlinked()`, `fun focus(key: String?)`. `GraphView.setGraph(state)`, `var onNodeTap: (GraphNode) -> Unit`, `fun recenter()`. Gestures: `ScaleGestureDetector` (0.3×–4×), drag on empty space = pan, drag on a node = move it (edges follow; positions kept in the view only), tap ≤ 8dp movement = `onNodeTap`. Hit radius ≥ 24dp. Search dims non-matching nodes (alpha 0.25) and draws labels of matches.
- Strings: `nav_network` (`Network` / `Rete`), `network_search` (`Search the network` / `Cerca nella rete`), `network_unlinked` (`Notes without links` / `Note senza collegamenti`), `network_recenter`, `network_empty` (`Write [[ in a note to link it to another one` / `Scrivi [[ in una nota per collegarla a un'altra`), `network_open` (`Open`/`Apri`), `network_new_linked` (`Linked note` / `Nota collegata`), `network_only_its_links` (`Only its links` / `Solo i suoi`), `network_ghost_message` (`This note does not exist yet. It is cited in %1$s.` / `Questa nota non esiste ancora. È citata in %1$s.`), `network_create` (`Create note` / `Crea nota`), `network_not_now` (`Not now` / `Non ora`), `network_counts` (plurals-free: `%1$d links · linked from %2$d` / `%1$d collegamenti · collegata da %2$d`).

- [ ] **Step 1: Failing tests:** `NetworkViewModelTest.demoGraph` (fake index with demo + Genesis → 70 nodes, positions for all keys), `focusRestrictsGraph`, `toggleUnlinkedAddsLonelyNotes`; `GraphViewTest.tapOnNodeReportsIt` (set graph with one node at center, dispatch down/up at center → `onNodeTap` called), `dragOnEmptyPans` (no tap), `dragOnNodeMovesIt`.
- [ ] **Step 2: Run** `--tests '*NetworkViewModelTest*' --tests '*GraphViewTest*'` → FAIL.
- [ ] **Step 3: Implement** view model, `GraphView` (draw edges `colorOutlineVariant` 1dp, hub/real nodes `colorPrimary`/`colorOnSurface`, ghosts dashed `colorOutline`, radius `6dp + 2dp·sqrt(degree)` capped 18dp, focused edges `colorPrimary` 1.6dp), fragment (search pill, chips, FAB "+" = `network_new_linked` → pick source via existing search pattern then title dialog → `createOrOpen` + navigate, empty state), `NodeSheet` (plain `BottomSheetDialog` built in the fragment, no lambdas in arguments — see `OPEN_ISSUES.md` BottomSheet note; real: title, `network_counts`, first 160 chars, Open / Linked note / Only its links; ghost: `network_ghost_message`, Not now / Create note).
- [ ] **Step 4: Device test** `NetworkTest.drawerOpensNetworkAndNodeOpensNote`: insert notes A=`"[[B]]"`, B; open drawer, tap `nav_network`; `onView(withId(R.id.graph))` displayed; call `graph.onNodeTap(node B)` on the main thread; sheet shows "B"; click `network_open` → editor shows title "B".
- [ ] **Step 5: Run** unit tests → PASS; push → CI `preview` green (device tests). **Step 6: Commit** `feat(links): Network screen with draggable graph`

### Task 10: Screenshots and docs

**Files:** Modify `scripts/capture-ui.sh` (open drawer → Network, screenshot `network`; open "The Bible" note, screenshot `linked-note`; dark mode `network-dark`), `docs/CODEMAP.md` (row "Linked notes / Network": the files above), `docs/FEATURES.md`, `docs/DECISIONS.md` (#22 as in spec "Docs to update"), `docs/CHANGELOG-AGENTS.md`, `docs/handoff/README.md` (state + canvas link), `docs/handoff/OPEN_ISSUES.md` (limits: rename does not update links; duplicate titles → newest; embeds/tags/aliases later).

- [ ] **Step 1:** update script and docs. **Step 2:** push; CI `preview` artifact `ui-screenshots` contains `network.png`, `network-dark.png`, `linked-note.png`; look at them (Read the PNGs) and compare with the canvas screens 2, 5, 9. **Step 3: Commit** `docs: linked notes and Network`.
- [ ] **Step 4:** open the PR (`feat: linked notes and Network view`), wait for green CI, owner merges, then publish the release (standing approval, `docs/handoff/RELEASE.md`) as **0.8.0**.
