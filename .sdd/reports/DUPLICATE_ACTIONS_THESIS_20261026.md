# Duplicate actions — thesis audit, 2026-10-26

Read-only source audit, no APK or runtime claim. Baseline HEAD 02b6dc9. Parent coordinates; executor inspected Android sources. Principle: preserve distinct commit scopes and navigation affordances; remove proven simultaneous duplicates only.

## 1. Notes: two visible Done controls really are equivalent

Working tree NowPlayingScreen.kt:777-799 shows the header Done while notes focus/IME is active. Inner Notes renderer at 1297-1318 always shows another Done. Both hide keyboard, clear focus and invoke the same saveNotes callback (410-420). Header/render duplication also exists in HEAD: header condition at 795, notes title at 1303 and inner callback at 1312. This is not introduced by the uncommitted playback/source changes.

Minimal recommendation: retain the editor-level Done, which also serves the landscape renderer, and suppress the duplicate portrait header Done; preserve a usable header/navigation policy while focused. Alternatively delegate one explicit action slot by layout, but do not simply delete the inner button: landscape calls the same renderer (working tree 715-718). Verify both layouts with keyboard hidden/visible. System Back already performs save on active notes editing (577-588); removing a button must not change that behavior. Source inspection alone does not prove asynchronous persistence survives navigation.

## 2. Notes title and placeholder overlap, but placeholder contains useful guidance

Working tree NowPlayingScreen.kt:1292-1295 and 1329-1331 use notes_title and notes_placeholder. Both RU/EN strings repeat Personal song notes; placeholder additionally supplies key/capo/structure (values/strings.xml:82-83 and values-ru/strings.xml:82-83). Reduce placeholder to concrete examples, retain a clear field/screen identity. Do not remove useful examples merely because their prefix repeats the heading. Main string catalogs are foreign-dirty: edit only these specific values or a dedicated new resource, do not stage whole catalogs.

## 3. Studio Save draft vs Apply timings are distinct effects, not duplicates

KaraokeStudioDialog.kt:195-203 automatically saves a draft; close path 140-149 saves the draft before dismissal and exposes failure. Apply at 409-436 exports LRC and invokes onSaveLrc at 426, then updates expected source state. These are different persistence levels: local recoverable draft versus published song timing. Preserve both semantics. The nested EditLyricsDialog callback at 473 changes studio text only, whereas Main's callback NowPlayingScreen.kt:1140-1161 persists song lyrics. Identical-looking save wording in separate modal contexts is not evidence of duplicate simultaneous actions. KaraokeStudioDialog.kt and EditLyricsDialog.kt have no worktree delta versus HEAD.

## 4. Notes overflow Save is redundant in effect, but not necessarily simultaneously visible

NowPlayingOverflowMenu.kt:51 exposes Save Notes only in Notes mode; action definition at 108. Main focused portrait header swaps overflow for Done (777-805), so claiming three simultaneous buttons from source would overstate the defect. Inner Done remains available and overflow Save may be another route to same callback. Minimal recommendation: one discoverable primary completion action, omit the overflow Save only once both orientation/keyboard paths retain it. Other contextual items are meaningfully distinct: calibration only Karaoke (53), translation only Lyrics (50), edit/sync only lyric modes (45-49). NowPlayingOverflowMenu.kt has no worktree delta versus HEAD; no blanket menu deletion justified.

## Limits

No device layout, focus, save-error or recreation acceptance performed. Main worktree NowPlayingScreen has unrelated clock/source edits (git diff numstat 103 additions/109 deletions), legacy LrcTapSyncStudioDialog also differs (31/34); do not include those foreign changes in this bounded cleanup. Actual Main opens KaraokeStudioDialog at 1111-1129, so the legacy dialog is not evidence of duplicate current studio controls. Prefer a bounded notes cleanup, not a save-system rewrite.
