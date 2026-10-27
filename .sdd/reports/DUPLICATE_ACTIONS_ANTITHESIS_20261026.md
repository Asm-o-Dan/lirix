# Duplicate actions вЂ” antithesis audit, 2026-10-26

Scope: source inspection only, no APK/build/product/test changes. Baseline HEAD `02b6dc9`; working tree contains unrelated changes. References explicitly distinguish both. Two confirmed UX findings, one adjacent preservation risk. No device-runtime acceptance claimed.

## 1. Confirmed: two simultaneous Done actions perform the same operation

High confidence / P2. Worktree `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt:777-799` displays a header Done while Notes has focus or the IME is visible. The Notes renderer always displays a second Done at `1297-1318`; both hide keyboard, clear focus, report focus false, and call the same saveNotes callback (renderer wiring `715-718`, `936-939`). This is simultaneous duplication, not actions for separate editor stages. It matches user screenshot 3963.

Committed baseline contains the same defect: HEAD02b6dc9 NowPlayingScreen.kt header `795-817`, renderer `1307-1328`; shared save callback `407-418`. The unrelated PlaybackUiClock/source changes in the dirty main file are not required to reproduce it.

Minimal correction: remove only the portrait header Done and preserve the inner Notes editor Done. The inner action already exists unconditionally in the Notes renderer used by portrait and landscape, so no new ownership abstraction is necessary. Do not blindly remove the renderer action globally. Test actual production layout with focused Notes and keyboard hidden/shown, portrait/landscape/focus mode: exactly one actionable completion, eventual persisted note, no inaccessible editor exit.

## 2. Confirmed: empty Notes repeats its title in the placeholder

High confidence / P3. Worktree NowPlayingScreen.kt `1292-1295` shows notes_title, then `1329-1330` shows notes_placeholder in the empty field. RU resources `app/src/main/res/values-ru/strings.xml:82-83` repeat вЂњР›РёС‡РЅС‹Рµ Р·Р°РјРµС‚РєРё Рє РїРµСЃРЅРµвЂќ before listing examples. HEAD02b6dc9 has the same two UI consumers at `1302-1305` and `1339-1340`. Resource line references are current worktree (general resource files have foreign deltas; do not stage them wholesale).

Minimal correction: keep meaningful field identification once, change hint to examples only (key, capo, structure) in RU/EN. Do not remove accessible labeling together with cosmetic title. Verify empty and nonempty notes, font2, both locales/orientations; hint must add information rather than repeat the section title.

## Adjacent preservation risks, not automatic added scope

Worktree NowPlayingScreen.kt `410-420` launches saving in the screen scope and emits no pending/error state; Back `578-588` invokes save only while Notes focus/IME is active, otherwise navigates. HEAD equivalents `407-418`, `600-610`. Therefore UI cleanup must not replace both controls with navigation-before-save or claim reliable persistence merely from a callback assertion. Verify resulting Room rows after Done/Back, including failures and rapid subsequent navigation. Exact loss reproduction not run; no claim of confirmed data loss.

EditLyricsDialog.kt is byte-equivalent to HEAD after CRLF normalization. Its Close at `129-139` and Save at `173-176` are different operations, not duplicates. Its draft uses remember(initialLyrics) at `90` and direct dismiss at `93`/`132`; dirty-exit protection is an adjacent risk, not evidence to delete Close. Studio has separate recording/preview/save stages; no extra duplicate finding is asserted from labels alone.

Recommendation: fix the two proven Notes defects first with a single completion-ownership policy and a result-based save/back regression. Keep broader dirty-draft safety or studio redesign out of this small UI change unless failure is reproduced and separately scoped.
