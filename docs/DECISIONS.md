# Decisions log

Newest last. Format: date — decision — why.

1. **2026-11-08 — Base = Quillpad `master` @ `c12ff24d15bad1852332164812f89c00177c2967`** ("Version bump", 2026-10-08,
   Quillpad 1.5.14 / versionCode 56). Full upstream Git history kept. Remote `upstream` is fetch-only (push URL disabled).
2. **2026-11-08 — applicationId `io.github.mancio24x.blackpearl`.** Derived from the owner's GitHub account, so it is
   not Quillpad's (`io.github.quillpad`) and both apps install side by side. Must never change after first public release.
   FileProvider / androidx-startup authorities and the reminder action already use `${applicationId}` / `BuildConfig.APPLICATION_ID`.
   Static shortcuts (`xml-v25/shortcuts.xml`) hard-code the id and were updated by hand.
3. **2026-11-08 — Kotlin package and `namespace` stay `org.qosp.notes`.** Renaming would touch every file, break
   upstream cherry-picks and gives users nothing.
4. **2026-11-08 — Deep-link scheme `quillpad://` → `blackpearl://`** to avoid intent ambiguity when both apps are installed.
5. **2026-11-08 — Version restarts at `0.1.0` / versionCode 1.** Different app id = new app for Android; versionCode
   must only grow from here.
6. **2026-11-08 — Removed inherited Infigraph agent configs** (`.cursor`, `.kiro`, `.windsurf`, `GEMINI.md`,
   `.github/copilot-instructions.md`, `.claude/CLAUDE.md`, old `AGENTS.md`): they required an MCP tool not available to
   this project. Replaced by `AGENTS.md` + `CLAUDE.md`. Upstream workflows (guarded by `github.repository == 'quillpad/quillpad'`,
   Firebase Test Lab, Play bundle) removed and replaced by `build.yml` / `release.yml`. `FUNDING.yml` removed (upstream's).
7. **2026-11-08 — About screen** keeps Quillpad website/contributors/support links as attribution; only the "source code"
   link points to Black Pearl.
8. **2026-11-08 — Icon** = owner-provided artwork (`graphics/black-pearl/icon-source.png`), cropped to its dark square;
   adaptive foreground = inset bitmap (`mipmap-*/ic_launcher_art.png`), gradient vector background, vector monochrome
   (`graphics/black-pearl/monochrome.svg`). Upstream Quillpad artwork in `graphics/` kept for attribution/history.
9. **2026-11-08 — Release signing** via `-Pkeystore/-Pstorepass/-Pkeyalias/-Pkeypass` (inherited mechanism),
   keystore stored base64 in GitHub secret `KEYSTORE_BASE64`. Releases are created as **drafts**; the owner publishes them.
10. **2026-11-08 — Material 3 by theme migration, not screen rewrite.** Parent theme `Theme.Material3.DayNight`;
    app color attrs reference M3 roles; widget styles → Material3. Screens/layouts otherwise unchanged.
11. **2026-11-08 — Palettes are generated** with Google's Material color utilities (bundled in the Material library)
    by `tools/palette-gen`, as static XML (works on API 24+). "Follow system" and "My color" use `DynamicColors`
    (Android 12+ only; hidden on older devices). Preference values are enum names: never rename existing constants
    (guarded by `AppearancePreferencesTest`).
12. **2026-11-08 — Customization = runtime theme overlays** (`Shape.*`, `NoteFont.*`, palettes, AMOLED) applied in
    `BaseActivity`, mirroring the Atlante approach (palettes + personal theme) without new libraries.
13. **2026-11-08 — Image layout stored in the note text** as a standalone Markdown image line
    `![alt](path "=align,width")` (see `ImageBlocks`). No DB/backup/sync format change, no migration; position survives
    close, restart, backup, sync and export because it is plain text. Files stay attachments (deleting an attachment
    also removes its image lines). Text wrapping beside images: not implemented (Markwon/TextView can't flow text
    around a drawable without replacing the renderer). Drag & drop: not implemented; contextual menu instead.
14. **2026-11-08 — Note colors reuse the existing `NoteColor` column** (no new table/field, no migration). Tints stay
    per light/dark theme; `NoteColorContrastTest` enforces WCAG AA (≥ 4.5:1) of note text on every color for every
    palette (dark Cyan darkened to #22606F to pass).
