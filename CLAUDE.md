# CLAUDE.md — Black Pearl

@AGENTS.md

All project rules are in `AGENTS.md` (imported above). Claude Code specifics only:

- Prefer the Grep/Read tools with `offset`/`limit` over reading whole files; `EditorFragment.kt` (~1300 lines)
  and `no_translate_strings.xml` are large — read ranges.
- Do not spawn Explore subagents for questions `docs/CODEMAP.md` already answers.
- Use the task list for multi-step work; one feature branch per task.
- Long Gradle commands (`assembleDebug`, full `test`) take minutes: run them in the background.
- The project owner is not an advanced programmer: when a decision is needed, explain the trade-off in plain words
  and recommend one option.
