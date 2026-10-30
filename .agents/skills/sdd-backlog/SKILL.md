---
name: sdd-backlog
description: "Maintain project deferred work in .sdd/backlog: add, list, verify, triage, or explicitly work selected items. Use when user mentions backlog or asks to defer or revisit a finding. Active work stays in .sdd/board.md and follows .sdd/WORKFLOW.md."
metadata:
  short-description: Manage deferred SDD work
---

# SDD Backlog

Maintain a durable list of real work that is intentionally not active yet. The backlog is a parking place, not a second task board: active tasks belong in `.sdd/board.md`, and accepted designs belong in `.sdd/specs/` and related SDD artifacts.

## Storage and item format

Store one item per file in `.sdd/backlog/<slug>.md`. Do not create an empty backlog directory just to report that it is empty.

Use this frontmatter:

```yaml
---
worth: later
where: app/src/path/File.kt:123
added: 2026-10-25
---
# Short description of the deferred issue

Why this is real, how it was found, why it is deferred, and any evidence or links.
```

- `worth: yes` means the value of doing the work is agreed. It does not mean it must be scheduled now.
- `worth: later` means the value decision is unresolved. State the unknown or condition that would settle it.
- `worth: no` records a decision not to do the work. Keep the rationale while it prevents rediscovery and re-argument.
- `where` is optional. Use a repository-relative `path:line` only when the issue is anchored to a specific location. The line is a navigation hint and can move.
- `added` is the date first recorded; do not refresh it when editing an item.

The slug should name the issue, not merely the file it affects. The body has no fixed subheadings: keep small items short and include enough evidence to make larger ones understandable.

## Add or defer an item

When the user asks to add an item, or explicitly accepts an offer to defer a finding:

1. Confirm it is real work that is not being done in the current scope. Do not use backlog for active tasks, speculative ideas without a concrete reason, or ordinary implementation steps.
2. Search existing backlog items by issue and description. A shared file path alone does not make two findings duplicates. If the same issue exists, update its evidence or `worth` in place; do not create a second file.
3. Verify `where` against the current tree and describe why the work is deferred. If value is genuinely undecided, use `later` and name what would settle it.
4. Before writing, check the current Git branch and the repository’s default branch. If the checkout is not on the default branch, do not switch branches or create a worktree. Tell the user which branch is active and ask whether to record the repository-wide note in this checkout.
5. Create the item only after the branch condition is satisfied. Do not add it to the active board, stage it, commit it, push it, or post it to a PR unless the user asks.

When a task or review surfaces additional deferred work, offer to record it; never write it silently.

## List and inspect

For a list request:

1. Read each `.sdd/backlog/*.md` item. If the directory is absent or empty, report that plainly and do not create it.
2. Check the frontmatter and verify every `where` location still exists and still supports the item’s claim. Report stale locations as stale; do not present them as confirmed work.
3. List items in `yes`, `later`, then `no` order, oldest `added` first within each group. Include `where` when present. Keep the summary concise and factual.
4. If the user asks what to do next, recommend an item using current evidence. Brief effort, blast radius, and current user impact so the user can judge the recommendation. Reading or listing the backlog does not authorize implementation or deletion.

For a named slug, read and verify only that item. If it does not exist, report that and list available slugs; do not guess a similar filename.

## Triage and work an item

Treat each item as a separate decision. Recheck its evidence and `where` before acting because the repository may have changed since it was filed.

- **Fix:** Start only when the user explicitly selects the item or clearly asks to work it. Re-evaluate scope, complexity, and impact from the current repository; backlog notes are clues, not approved design. Follow `.sdd/WORKFLOW.md` to choose the active-work level. Move necessary requirements into `.sdd/intake/`, specs, contracts, tasks, and the board as appropriate. Follow current project instructions for testing and review.
- **Keep for later:** Leave the item unchanged unless its evidence or value has changed.
- **Close as not worth doing:** Set `worth: no` and write the reason if that rationale will prevent rediscovery. Delete the file only when it no longer provides useful context or the user explicitly asks to remove it.

After a selected fix is complete and accepted, delete its backlog file in the same working change as the fix. Do not stage or commit automatically. Never delete an item merely because it was read, listed, or moved into active work.

Do not invent an in-progress marker or checkbox in the backlog. `.sdd/board.md` owns active-task status.
