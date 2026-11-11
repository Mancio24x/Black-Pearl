# Network: depth, tags and attachments, timelapse — design

Status: scope approved in chat on 2026-11-11 (items 5, 6, 7 of the Obsidian comparison; tags option "A").
Builds on `2026-11-11-linked-notes-design.md`. Not implemented yet.

## Goal

Three more Obsidian graph features in the Network screen, without changing anything for people who do not use them:
1. **Depth** of the local graph ("Show only its links"): 1 to 5 levels instead of 1.
2. **Tags and attachments as nodes**, each behind its own chip, off by default.
3. **Timelapse**: notes appear one by one in creation order.

Success criteria:
- With the new chips off and no timelapse started, the Network looks and behaves exactly as in 0.8.3.
- No Room schema change, no backup or sync change, note text never rewritten.
- Tag `#prayer` written in a note and the Black Pearl tag "prayer" are the same node.

Out of scope: colour groups, a settings panel, opening the attachment file itself from the Network,
remembering the chip choices after leaving the screen.

## What the user sees

**Chip row** (today: "Show unlinked notes", "Show all", "Recenter"). New filter chips **Tag** and **Attachments**, both
off when the screen opens (kept while the screen stays open, like "Show unlinked notes").

**Tags (chip on).**
- One node per tag, title `#name`, drawn as a small filled rounded square in the theme's tertiary colour
  (notes stay primary circles, ghosts stay dashed circles). Size grows with the number of notes, like notes.
- An edge from each note to each of its tags. A note whose only connection is a tag counts as linked.
- Tags come from two places, merged by name, case-insensitive, first spelling met is shown:
  - Black Pearl tags (`Note.tags`, also filled by the Obsidian importer from front matter);
  - Obsidian inline tags in the text: `#name` at the start of a line or after a space/punctuation, name made of
    letters, digits, `_`, `-`, `/` with at least one non-digit (`#2024` is not a tag, `#a/b` is the tag `a/b`).
    Not inside inline code, fenced code, `[[links]]` or URLs; `# Heading` is not a tag.
- Tap a tag node → bottom sheet "Notes with #name" listing them; tapping one opens it. Long press → same sheet.

**Attachments (chip on).**
- One small node per attachment of a visible note (photo, audio, video, file), outlined square in `onSurfaceVariant`,
  title = file name (else description, else the type: "Photo", "Audio", "Video", "File"). Edge to its note.
- Tap → opens the note that holds it. Empty attachments are skipped.

**Depth.** When "Show only its links" is active, a Material slider "Levels 1–5" appears in the chip row (default 1).
Level N keeps every node reachable in N steps from the chosen note, following links, and tags/attachments when
their chips are on. All edges between kept nodes are drawn (today only the edges touching the chosen note; Obsidian
draws all of them). "Show all" hides the slider again.

**Timelapse.** A ▶ button in the Network top bar.
- Starts from an empty canvas; notes appear in order of creation date, with their edges once both ends are visible.
  Ghosts, tags and attachments appear with the first note connected to them.
- Duration: 80 ms per note, at least 3 s, at most 12 s. Positions are the current ones (no extra movement).
- Any touch on the graph, Recenter, a chip or ▶ again stops it and shows the full graph.

## Compatibility

- Inline-tag rules follow Obsidian's (above). Text is only read, never changed.
- Black Pearl tags are not created from inline `#tags` (that would change notes and sync); the Network merges them for
  display only.

## Architecture

| Unit | Change |
|---|---|
| `components/links/InlineTags.kt` (new, pure Kotlin) | `find(markdown): List<String>` with the rules above; reuses WikiLinks' code-range exclusion (made `internal`) |
| `components/links/LinkGraph.kt` | `GraphNode.kind` (`NOTE`, `MISSING`, `TAG`, `ATTACHMENT`); `build(index, showUnlinked, focusKey, depth = 1, tags = false, attachments = false)`; BFS for depth; edges among kept nodes |
| `components/links/LinkIndex.kt` | keeps each visible note's tags (app + inline, merged) for the graph |
| `ui/links/GraphView.kt` | draws the new node shapes; `kind` replaces `noteId == null` checks; timelapse (`playTimelapse(appearAt)`, stop on touch) driven by Choreographer like the physics |
| `ui/links/NetworkViewModel.kt`, `NetworkFragment.kt`, `fragment_network.xml` | two chips, depth slider, ▶ action, tag sheet, attachment tap |
| strings | `values/strings_blackpearl.xml` + `values-it/` |

Live dragging (`GraphPhysics`) and highlight work on the new nodes unchanged; the 300-node limit counts them too.

## Errors and edge cases

- Hidden notes and notes in the bin: their tags and attachments are not shown (same as notes).
- A tag used by one note only still gets a node (Obsidian does the same).
- Very many tags or attachments can pass 300 nodes: then live dragging switches off, as today.
- Timelapse with 0 or 1 note: the button does nothing visible beyond showing the graph.
- Notes with equal creation dates appear together.

## Testing

- JVM: `InlineTagsTest` (start of line, after punctuation, digits only, nested, code, links, URLs, headings),
  `LinkGraphTest` (tag merge app + inline + case, attachments, chips off = today's graph, depth 1–5 BFS, edges among
  kept nodes, hidden/bin excluded).
- Robolectric: `GraphViewTest` (timelapse shows nodes progressively and a touch shows all; tag/attachment tap
  callbacks).
- Device + screenshots: existing Network test still passes; preview job captures Network with tags on.

## Docs to update when implemented

`docs/CODEMAP.md` (InlineTags), `docs/FEATURES.md`, `docs/CHANGELOG-AGENTS.md`.
