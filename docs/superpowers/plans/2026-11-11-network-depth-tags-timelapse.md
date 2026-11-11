# Network depth, tags/attachments and timelapse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 1–5 level local graphs, optional tag and attachment nodes, and a creation-order timelapse to the Network screen.

**Architecture:** Pure logic stays in `components/links` (new `InlineTags`, extended `LinkGraph`/`LinkIndex`). The UI
(`GraphView`, `NetworkFragment`, `NetworkViewModel`) only gets new inputs and drawing. The timelapse uses Choreographer
the same way `GraphPhysics` does.

**Tech Stack:** Kotlin, Android Views, Material 3 (Chip, Slider), JUnit4 + Robolectric (sdk 34), no new dependency.

**Spec:** `docs/superpowers/specs/2026-11-11-network-depth-tags-timelapse-design.md`

## Global Constraints

- No Room schema, backup or sync change; note text is only read.
- The new chips are off when the screen opens. With them off and no timelapse, the graph is identical to today's,
  except for the edges between neighbours in the local graph.
- Tag names are merged case-insensitively (`Locale.ROOT`), and the first spelling met is shown as `#name`.
- Strings go in `values/strings_blackpearl.xml` plus `values-it/strings_blackpearl.xml`.
- No local Android build is possible: tests run on CI (`build` = unit + lint + assembleDebug, `preview` = device).

## Review Focus

1. A tag inside a `[[Note#Heading]]` link, a URL (`https://x.y/#frag`) or code → not a tag (InlineTagsTest).
2. `#2024` and a bare `#` → not tags; `#2024a` and `#a/b` → tags (InlineTagsTest).
3. Hidden or bin notes with tags/attachments → no tag or attachment node from them (LinkGraphTest).
4. A touch during the timelapse → full graph at once, with no stuck half state (GraphViewTest).
5. Depth above the graph's diameter → same result as the maximum depth, with no crash (LinkGraphTest).

---

### Task 1: InlineTags parser

**Files:**
- Create: `app/src/main/java/org/qosp/notes/components/links/InlineTags.kt`
- Modify: `app/src/main/java/org/qosp/notes/components/links/WikiLinks.kt` (make `excludedRanges` `internal`, and add `internal fun linkRanges(text): List<IntRange>` from the `LINK` regex)
- Test: `app/src/test/java/org/qosp/notes/components/links/InlineTagsTest.kt`

**Interfaces:**
- Produces: `object InlineTags { fun find(markdown: String): List<String> }`. It returns tag names without `#`, in order and distinct by case-insensitive key, keeping the first spelling.

- [ ] **Step 1: Write the failing tests**

| Input | Expected `find` |
|---|---|
| `"#prayer at start\nand #Faith, then (#hope)."` | `["prayer","Faith","hope"]` |
| `"#2024 and #2024a and #a/b and # Heading"` | `["2024a","a/b"]` |
| `` "`#code` and\n```\n#fenced\n```\n#ok" `` | `["ok"]` |
| `"[[Note#Heading]] https://x.y/#frag word#mid"` | `[]` |
| `"#Same and #same"` | `["Same"]` |

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*InlineTagsTest*'`. CI only; locally this is a compile check through the `build` job. Expected: FAIL.
- [ ] **Step 3: Implement.** Regex `(?<![\p{L}\p{N}_/#&\]\[:.-])#([\p{L}\p{N}_/-]+)`. Keep a match only if it contains a non-digit, does not end in `/`, and its start is outside `excludedRanges(text) + linkRanges(text)`. The lookbehind rejects `word#x` and `/#frag`.
- [ ] **Step 4: Run tests.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(links): Obsidian inline tag parser`.

### Task 2: Graph model — kinds, tags, attachments, depth

**Files:**
- Modify: `app/src/main/java/org/qosp/notes/components/links/LinkGraph.kt`, `LinkIndex.kt`
- Modify callers of `noteId == null` ghost checks: `ui/links/GraphView.kt`, `ui/links/NetworkFragment.kt`. Use `kind` only; keep the same behaviour.
- Test: `app/src/test/java/org/qosp/notes/components/links/LinkGraphTest.kt`

**Interfaces:**
- Consumes: `InlineTags.find`.
- Produces:
  - `enum class NodeKind { NOTE, MISSING, TAG, ATTACHMENT }`
  - `data class GraphNode(val key: String, val title: String, val noteId: Long?, val degree: Int, val kind: NodeKind = if (noteId != null) NodeKind.NOTE else NodeKind.MISSING)`. For an attachment, `noteId` = the owning note's id.
  - Keys: `tag:<lowercase name>` with title `#<first spelling>`, and `file:<noteId>:<index in note.attachments>`.
  - The title of an attachment node is fileName, else description, else `""`. The UI maps `""` to a type label in Task 4, so the node also needs `attachmentType: Attachment.Type? = null` in `GraphNode`.
  - `LinkIndex.tagsOf(noteId: Long): List<String>` gives app `Tag.name` + inline tags, merged by key.
  - `LinkGraph.build(index, showUnlinked = false, focusKey: String? = null, depth: Int = 1, tags = false, attachments = false)`.

- [ ] **Step 1: Write the failing tests** in `LinkGraphTest`:
  - `tagsOffGivesTodaysGraph`: build with defaults equals the nodes and edges the existing tests expect. Update `focusShowsNeighboursOnly` to also expect the edge between kept neighbours when one exists.
  - `appAndInlineTagsMerge`: note 1 has app tag `Tag("Prayer")`; note 2 has content `"#prayer"`. With `tags = true` there is one node `tag:prayer` titled `#Prayer`, kind TAG, degree 2, and edges `note:1→tag:prayer` and `note:2→tag:prayer`.
  - `tagOnlyNoteCountsAsLinked`: a note with only a tag appears when `tags = true` and `showUnlinked = false`.
  - `attachmentsBecomeNodes`: a note with 2 attachments (one `isEmpty()`) and `attachments = true` gives exactly one ATTACHMENT node `file:1:0` with `noteId = 1`.
  - `hiddenAndBinNotesGiveNoTagsOrFiles`.
  - `depthFollowsLinks`: chain A→B→C→D→E→F with focus on A. Depth 1 gives {A,B}, depth 3 gives {A,B,C,D}, depth 9 gives all 6. Edges are those among kept nodes.
  - `depthCrossesTags`: A and B share `#x` with tags on, focus A, depth 2 gives {A, tag:x, B}.
- [ ] **Step 2: Run** `--tests '*LinkGraphTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - Add tag and attachment edges into the same `neighbours` and `edgeSet` before the degree computation, only when their flag is on.
  - Focus is a BFS over `neighbours` up to `depth.coerceIn(1, 5)`. The spec's maximum is 5, so in `depthFollowsLinks` depth 9 gives {A..F} because F is 5 steps from A.
  - Edges = `edgeSet` filtered to both ends kept.
- [ ] **Step 4: Run tests.** Expected: PASS (also `GraphLayoutTest` and `GraphPhysicsTest` unchanged).
- [ ] **Step 5: Commit** `feat(links): tag and attachment nodes, deeper local graph`.

### Task 3: GraphView — new shapes and timelapse

**Files:**
- Modify: `app/src/main/java/org/qosp/notes/ui/links/GraphView.kt`
- Test: `app/src/test/java/org/qosp/notes/ui/links/GraphViewTest.kt`

**Interfaces:**
- Consumes: `NodeKind`.
- Produces:
  - `fun playTimelapse(appearAt: Map<String, Long>)`: key → creation seconds, for notes only.
  - `fun stopTimelapse()`.
  - `val isPlayingTimelapse: Boolean`.
  - `internal val visibleKeys: Set<String>?`: null when not playing.

- [ ] **Step 1: Write the failing tests:**
  - `timelapseShowsNotesInCreationOrder`: three linked notes created at 1, 2, 3. After `playTimelapse` with 0 ms advanced, `visibleKeys` is empty. Idle the main looper for 1.6 s of the 3 s run and the first two notes are visible. After 3.5 s, `isPlayingTimelapse` is false and `visibleKeys` is null.
  - `touchStopsTimelapse`: play, then ACTION_DOWN, then `visibleKeys` is null.
  - `nodesAppearWithTheirFirstNote`: a ghost or tag linked only to the note created at 3 is not visible at 1.6 s.
- [ ] **Step 2: Run** `--tests '*GraphViewTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - **Duration:** `(notes * 80 ms).coerceIn(3000, 12000)`. A note is visible when `elapsed >= duration * rank / count`, where rank is the index in creation order and ties share the rank.
  - **Non-note nodes:** visible from the earliest visible note they connect to.
  - **Drawing:** `onDraw` skips invisible nodes, and edges with an invisible end.
  - **Shapes:**
    - TAG: filled rounded square, side = 2 × radius, `colorTertiary`.
    - ATTACHMENT: outlined square of 5 dp, `colorOnSurfaceVariant`.
  - **Stopping:** ACTION_DOWN, `recenter()` and `setGraph` call `stopTimelapse()`. Physics does not run while playing.
- [ ] **Step 4: Run tests.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(links): timelapse and tag/attachment shapes in the Network`.

### Task 4: Network screen — chips, depth slider, ▶, tag sheet

**Files:**
- Modify: `app/src/main/res/layout/fragment_network.xml`, `ui/links/NetworkViewModel.kt`, `ui/links/NetworkFragment.kt`
- Modify: `app/src/main/res/values/strings_blackpearl.xml`, `values-it/strings_blackpearl.xml`
- Create: `app/src/main/res/drawable/ic_play.xml` (only if no play icon exists: check `rg -l "M8,5v14l11,-7z" app/src/main/res/drawable`)
- Modify: `app/src/androidTest/java/org/qosp/notes/tests/NetworkNavigationTest.kt` (only if the existing test breaks)
- Docs: `docs/CODEMAP.md`, `docs/FEATURES.md`, `docs/CHANGELOG-AGENTS.md`

**Interfaces:**
- Consumes: `LinkGraph.build(..., depth, tags, attachments)`, `GraphView.playTimelapse/stopTimelapse`, `NodeKind`.
- Produces: `NetworkViewModel.showTags`, `showAttachments` (`MutableStateFlow(false)`), and `depth` (`MutableStateFlow(1)`), combined into `graph`.

- [ ] **Step 1: Layout.**
  - Filter chips `network_tags_chip` and `network_files_chip`, placed after `network_unlinked_chip`.
  - A `com.google.android.material.slider.Slider` `network_depth` with valueFrom 1, valueTo 5, stepSize 1, width 160dp, `visibility="gone"`. Put it after `network_focus_chip`.
  - A toolbar menu item ▶ (`network_timelapse`), added in code with `toolbar.menu.add`, `SHOW_AS_ACTION_ALWAYS`.
- [ ] **Step 2: Strings.**

  | Key | EN | IT |
  |---|---|---|
  | `network_tags` | Tags | Tag |
  | `network_files` | Attachments | Allegati |
  | `network_depth` | Levels | Livelli |
  | `network_timelapse` | Timelapse | Timelapse |
  | `network_tag_notes` | Notes with %1$s | Note con %1$s |
  | `network_file_photo` | Photo | Foto |
  | `network_file_audio` | Audio | Audio |
  | `network_file_video` | Video | Video |
  | `network_file_other` | File | File |
- [ ] **Step 3: Fragment wiring.**
  - **Chips** set the flows. The **slider** is visible only while `focus != null`; changing it sets `depth`.
  - **"Show all"** resets `depth` to 1.
  - **▶** calls `playTimelapse(index.visibleNotes.associate { "note:${it.id}" to it.creationDate })`, or `stopTimelapse()` if already playing.
  - **Tap on TAG** (tap and long press): bottom sheet titled `network_tag_notes`, listing the notes whose `tagsOf` contains it; tapping one opens it.
  - **Tap on ATTACHMENT:** opens `noteId`.
  - **`displayTitle`** for an ATTACHMENT with a blank title uses the type label string.
- [ ] **Step 4: Verify.** Push and check that CI `build` and `preview` are green. The device test `NetworkNavigationTest` must still pass.
- [ ] **Step 5: Docs and commit.**
  - CODEMAP: add the InlineTags row note. FEATURES: one sentence. CHANGELOG-AGENTS: an entry.
  - Commit `feat(links): Network chips for tags and attachments, levels slider, timelapse`.
