# Linked notes and "Network" view — design (base version)

Status: approved by the owner in chat on 2026-11-11 (option "1 base", entry point "A"). Not implemented yet.
Visual design: Claude Design canvas "Black Pearl — Note collegate", https://claude.ai/artifact/QNAmdLWMrpWjKYuYC5Ap48
(11 screens: drawer, Network + node sheets, linked note, create dialog, backlinks, `[[` suggestions, dark variants).

## Goal

Obsidian-style linked notes and a graph ("Network") view, **fully compatible with Obsidian** text, while the app
stays exactly as simple as it is today for anyone who does not use them.

Success criteria:
1. A note written in Obsidian with `[[links]]` shows working links in Black Pearl, and a note linked in Black Pearl
   opens correctly in Obsidian when the folder sync points at the vault (files are already `Title.md`).
2. Someone who never types `[[` and never opens the Network sees only one extra drawer row. No new editor button,
   no change to the note list, no new setting.
3. No Room schema change, no change to backup or sync formats. Note text stays plain Markdown.

Out of scope (later versions): embeds `![[Note]]`, `#tags` as Obsidian tags, aliases from front matter,
block links `[[Note#^id]]`, updating links when a note is renamed, an on/off setting.

## Owner decisions

| Question | Decision |
|---|---|
| Scope | Base: links, backlinks ("Linked from"), Network graph, create linked notes |
| How hidden | A: one drawer item "Network"; `[[` suggestions only appear when `[[` is typed; backlinks only when there are some |
| Link syntax | Obsidian's, unchanged in the stored text |
| Demo | Obsidian MOC note "The Bible" (English, unchanged), added once like the recipes sample |
| Look | Material 3, same style as the "Black Pearl — Redesign" canvas (rounded, Fraunces titles, Inter text), light + dark |

## What the user sees

**Drawer.** New item "Network" (`nav_network`, Italian "Rete") in the group with Archive / Bin, icon = three
connected dots. Nothing else in existing screens changes.

**Network screen** (new fragment, own nav destination):
- Search pill at the top (filters nodes by title, highlights matches, dims the rest).
- Graph canvas: one node per non-deleted note that has at least one link in or out, plus "ghost" nodes for link
  targets that do not exist yet (outlined, dashed, `onSurfaceVariant`). Node radius grows with its number of links.
  Archived notes are included; notes in the bin and hidden notes are not. Notes with no links are not drawn
  (a chip "Show unlinked notes" toggles them).
- Like Obsidian: drag the background to move the whole graph, drag a node to move it (its links follow), pinch to
  zoom. A "Recenter" button in the top bar puts everything back. Positions are not stored.
- Tapping a node opens a bottom sheet (no swipe-to-expand, owner's choice); **Open** goes to the note in the editor.
- Tap a node → bottom sheet: title, first lines of text, counts ("12 links · linked from 3"), buttons
  **Open**, **New linked note**, **Show only its links** (local graph, depth 1). Tap a ghost node → sheet
  "«Genesis» does not exist yet" with **Create note**.
- Round **+** FAB (same as the note list): "New linked note" → pick the source note (search list), then type the title.
- Empty state (no links anywhere): short explanation "Write [[ in a note to link it to another one" and a button
  that opens the demo note when it exists.

**In a note, reading (preview mode):**
- Each `[[Target]]`, `[[Target|label]]`, `[[Target#Heading]]` is drawn as a tappable pill (primary container color,
  rounded, shows `label` or `Target`). Ghost targets use the outlined style.
- Tap → opens the target note (scrolls to the heading for `#Heading` when found). Ghost → dialog
  "Create «Target»?" → creates a note with that title, in the same notebook as the source, and opens it.
- **"Linked from (N)"** card list at the bottom of the note, only if N > 0: title + the line containing the link.

**In a note, writing (edit mode):** after typing `[[`, a suggestion strip above the keyboard lists matching note
titles as the user types, then link targets that do not exist yet ("cited in …"), case-insensitive, prefix first,
then contains; max 8; last item "Create «typed text»". The strip takes the place of the formatting pill while open.
Tap → completes `[[Title]]` and puts the cursor after it. Typing `]]`, Escape or moving the cursor away closes it.
No toolbar button is added.

## Compatibility rules (Obsidian)

- Syntax recognised: `[[Target]]`, `[[Target|label]]`, `[[Target#Heading]]`, `[[Target#Heading|label]]`.
  Not inside inline code or fenced code blocks. `![[...]]` (embed) is left as text in this version.
- Target matching: by note title, trimmed, case-insensitive, also accepting a trailing `.md`
  (Obsidian matches by file name; folder sync writes `Title.md`). Path prefixes (`folder/Note`) match on the last segment.
- Labels are trimmed for display only (the demo has `[[010 Faith MOC|Faith MOC ]]`).
- Duplicate titles: link goes to the most recently modified one (Obsidian would ask for a path; documented limit).
- Stored text is never rewritten by rendering, suggestions write plain `[[Title]]`.
- **Import change:** `MarkdownLinks.resolve` (`components/importer/TextFormats.kt`) stops turning `[[Note|alias]]`
  into plain text; wiki links to notes are kept as written. File embeds (`![[photo.png]]`) keep today's behaviour.
- PDF export: links printed as their label (no pill). Nextcloud / folder sync / backup: unchanged text.

## Architecture

New package `org.qosp.notes.ui.links` (UI) and `org.qosp.notes.components.links` (pure logic). No new library.

| Unit | Purpose | Depends on |
|---|---|---|
| `WikiLinks` (object) | Parse `[[...]]` occurrences in a Markdown string → list of `(target, heading, label, range)`; skips code | nothing (pure Kotlin, unit-tested) |
| `LinkIndex` | From a list of notes: title → note map, outgoing links per note, backlinks per note, ghost targets | `WikiLinks`, `Note` |
| `LinkGraph` | Nodes + edges for the Network from a `LinkIndex`, with filters (search, local depth 1, show unlinked) | `LinkIndex` |
| `GraphLayout` | Simple force-directed layout (repulsion + springs, fixed iterations, seeded → deterministic), runs off the main thread | nothing |
| `NetworkFragment` + `NetworkViewModel` | Screen; observes `NoteRepository.getNonDeleted()`, builds index/graph | Koin, repository |
| `GraphView` | Custom `View`: draws nodes/edges with theme colors, gestures (scale, pan, tap, drag) | `GraphLayout` output |
| `WikiLinkPlugin` | Markwon plugin: pills + click spans in preview | `WikiLinks`, existing Markwon setup (`di/MarkwonModule.kt`) |
| `LinkSuggestions` | Edit-mode strip: detects open `[[`, filters titles, inserts text | `LinkIndex` titles |
| `BacklinksSection` | Card list under the note in `EditorFragment` (hidden when empty) | `LinkIndex` |

Data flow: notes Flow → `LinkIndex` (rebuilt on change, debounced; O(total text), fine for thousands of notes) →
Network graph / editor backlinks / suggestions. Nothing is persisted.

Large file note: `EditorFragment.kt` is ~1300 lines; the editor part goes in the new small units above, with only
thin hooks in `EditorFragment` (attach plugin, show backlinks, attach suggestions).

## Demo note "The Bible"

- Asset `app/src/main/assets/sample/The Bible.md` = the owner's file, unchanged (copy kept in
  `docs/superpowers/specs/linked-notes/The Bible.md`).
- Title "The Bible"; text = the file without the `# The Bible` line (it repeats the title). Not via
  `Titles.split("", …)`: that would take the first line `links: [[010 Faith MOC|Faith MOC ]]` as the title.
- Added once by `SampleNote`-like code with its own flag `demo_links_note_added` (users who already have the recipes
  note still get it). Local only, not synced. Deleting it is final.
- In the Network it shows one hub with 69 links (66 books, "God's unified story", "Biblical Themes and Images MOC",
  "010 Faith MOC"); all are ghosts until created.

## Errors and edge cases

- Link to a note in the bin → treated as ghost (creating asks first; never restores silently).
- Link to itself → drawn, no self-edge in the graph.
- Unclosed `[[` → plain text. Very long labels → pill ellipsized.
- Creating a note from a ghost when a note with that title appears meanwhile → open the existing one.
- App lock on: Network is inside the locked app (no extra exposure); widget unchanged.

## Testing

- Unit (JVM): `WikiLinksTest` (all syntaxes, code spans/blocks skipped, trailing spaces, `.md`, paths, unclosed),
  `LinkIndexTest` (backlinks, ghosts, case-insensitive, duplicates, bin), `LinkGraphTest` (filters, local graph),
  `GraphLayoutTest` (deterministic, no NaN, nodes separated), `LinkSuggestionsTest` (detection and insertion),
  `DemoLinksNoteTest` (title, text, 69 links), importer test: Obsidian sample keeps `[[links]]`.
- Device (CI emulator): drawer → Network opens; tap a node → sheet → Open shows the note; tap a pill in preview
  opens the target; ghost → create. UI preview job gets screenshots of Network (light/dark) and a linked note.
- Strings in `values/strings_blackpearl.xml` + `values-it/strings_blackpearl.xml`.

## Docs to update when implemented

`docs/CODEMAP.md` (new rows), `docs/FEATURES.md`, `docs/DECISIONS.md` (#22: wiki links kept as Obsidian text,
index in memory, no schema change), `docs/CHANGELOG-AGENTS.md`, handoff README.
