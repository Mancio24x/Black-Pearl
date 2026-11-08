<p align="center"><img src="graphics/black-pearl/icon-512.png" width="128" alt="Black Pearl icon"></p>

# Black Pearl

Black Pearl — An open-source Android note-taking app inspired by Quillpad, built for creativity, organization and privacy.

Black Pearl is a fork of [Quillpad](https://github.com/quillpad/quillpad) (GPL-3.0) and keeps all its features:
Markdown notes, checklists, attachments, reminders, notebooks, tags, Nextcloud / folder sync, backups, themes
and translations. The original README is in [docs/QUILLPAD_README.md](docs/QUILLPAD_README.md).

Planned Black Pearl features: repositionable images, improved note colors, offline OCR, PDF export
(see [docs/ROADMAP.md](docs/ROADMAP.md)).

## ⬇️ Download

**[Release page — APK + source code (zip / tar.gz)](https://github.com/Mancio24x/Black-Pearl/releases/latest)**

- Direct APK: [black-pearl latest release](https://github.com/Mancio24x/Black-Pearl/releases/latest) → *Assets* → `black-pearl-x.y.z.apk`
- **Obtainium** (automatic updates): add `https://github.com/Mancio24x/Black-Pearl`

## Try it on your phone (debug APK)

1. GitHub → **Actions** → **Build** → open the latest green run on `main`.
2. Scroll to **Artifacts** → download **black-pearl-debug-apk** (a zip) and extract `app-debug.apk`.
3. Copy it to the phone and open it (allow "install unknown apps" for your file manager/browser).

Debug builds are for testing only and are signed with a throwaway debug key: to move to a stable release later,
**make a backup** in the app (Settings → Backup), uninstall the debug build, install the release, restore.
Black Pearl uses its own app id (`io.github.mancio24x.blackpearl`), so it installs alongside Quillpad.

Stable signed releases: GitHub → **Releases**.

## For developers and AI agents

Start with [AGENTS.md](AGENTS.md), then [docs/CODEMAP.md](docs/CODEMAP.md). Build/test commands: [docs/TESTING.md](docs/TESTING.md).

## License and credits

GPL-3.0 — see [LICENSE](LICENSE). Based on Quillpad by Arumugam J, Michael Soultanidis and the Quillpad
contributors, itself a fork of Quillnote. All original copyright notices are preserved.
