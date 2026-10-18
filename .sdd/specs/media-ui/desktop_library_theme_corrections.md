# Desktop library + theme: bounded correction contract

Version1, 18 October2026. Design/source audit D-04/D-05; visual gate PENDING. No schema migration, playback change, new archive flow or broad layout redesign.

Coordinator status: APPROVED for DESK-UX-101–105 on 2026-10-18. QA first confirmed absent view-state APIs with a compile failure; no behavioral RED claimed. Product executor desktop_library_ux_coder; independent source review and actual preview inspection are separate gates. Hoisted notes drafts are incremental protection, not complete application-exit draft recovery.

## Data truth

`TrackEntity` has plainLyrics/syncedLyrics/isFavorite; chords exist only in `LyricsCacheEntity.chordsAmDm`, not in the current library track flow. Every persisted plain/LRC text is already local. Consequently current «Офлайн» and «С текстом / аккордами» cannot honestly be different filters. This batch exposes four filters: «Все», «Избранное», «Караоке», «С сохранённым текстом». Remove duplicate «Офлайн» and unsupported chord promise. Add small explanation under the filter: «Сохранённые тексты доступны без интернета. Аудиофайлы здесь не хранятся». Do not add cache observers solely to preserve an ambiguous label.

## Exact pure contract

New file `desktop/src/main/kotlin/com/lirix/app/desktop/library/DesktopLibraryViewState.kt`:

- `enum class DesktopLibraryFilter { ALL, FAVORITES, KARAOKE, SAVED_TEXT }`, each with Russian label above.
- `enum class DesktopLibraryEmpty { NONE, NO_HISTORY, NO_MATCHES }`.
- `data class DesktopLibraryViewState(val visibleTracks: List<TrackEntity>, val selectedTrack: TrackEntity?, val empty: DesktopLibraryEmpty, val hasRestrictions: Boolean)`.
- `fun buildDesktopLibraryViewState(tracks: List<TrackEntity>, query: String, filter: DesktopLibraryFilter, artist: String?, selectedKey: String?): DesktopLibraryViewState`.

Function preserves input order; query.trim() is case-insensitive substring in title/artist/album/plainLyrics only (existing behaviour). No regex. Empty/whitespace query has no restriction. Filter predicates: ALL=true; FAVORITES=isFavorite; KARAOKE=syncedLyrics has at least one parsed timestamp via existing `LrcSyncEngine.parseLrcLinesStrict(...).isNotEmpty()`; SAVED_TEXT=plainLyrics.isNotBlank OR syncedLyrics.isNotBlank. A malformed nonblankLRC is savedtext but not karaoke. Artist restricts exact artist string as existing grouping does. hasRestrictions=true if querynonblank/filter!=ALL/artist!=null.

Selection: preserve selectedKey iff among visibleTracks, otherwise first visibleTrack; emptyresult alwaysselectedTrack=null. This batch may not discard unsaved notes when resolving a new selection: until draftguard lands, hoist notesdrafts keyed by trackKey in DesktopWorkspace (ephemeral session map), pass selectedtrackdraft and update callbacks to panel; leaving/reentering Library retains drafts. Save clears draft only after DBflow confirms matching notes; error retains draft. No forced autosave/DBwrite on navigation. If this cannot be included atomically, ship draftguard first and hold selection UI integration.

Empty: tracks.isEmpty → NO_HISTORY, «В медиатеке пока нет треков» + «Включите музыку в плеере ПК — Lirix добавит прослушанное в историю». Nonempty tracks but visibleempty → NO_MATCHES, «Ничего не найдено» + «Измените запрос или сбросьте фильтры» + action «Сбросить поиск и фильтры». It clears query, filterALL, artistnull and artistModefalse in one click. When empty, detail shows no old title/actions/lyrics; one quiet placeholder «Выберите трек из списка» is permitted. Neither empty state offers playback of nonexistent local files.

UI input search label «Поиск по названию, артисту, альбому и тексту». Artist mode derives groups from visibleTracks; choosing group sets artist and switches track mode. Header shows artist and results count with back/clear action. Do not infer active track from this selection or interrupt playback.

## Theme contract

`DesktopDeckScreen` owns local `showThemeSelector` boolean, exactly like existing Workspace themeDialog. TopBar callback changes it true; render existing shared `ThemeSelectionDialog(selectedTheme,onThemeSelected)` dismiss=false. Opening/dismissing/cancelling must not call onThemeSelected. Selecting one of AppThemeType.entries invokes persistence callback with that exact enum; selecting current theme is harmless. No dark/light swap or fallback enum. Sidebar and Settings retain same enum persistence path. No new theme storage.

`DesktopTopBar` themeIconButton displays Palette icon, contentDescription «Тема оформления: <selectedTheme.title>», hovertooltip same title plus «Выбрать одну из шести тем». Keep current geometry in this batch. This is a selector, not toggle. All6 existing themes retained, no palette retune.

## Acceptance matrix

Pure viewstate tests: emptyDB, trimquery, case-insensitive textsearch, favorite-only, properLRC, malformedLRC, whitespace text, artist+search intersection, stale/missingselection, preservevalidselection and stableorder. Demonstrate duplicateofflinefilter removal in actualUI.

Integrationmanual: search misses with DBnonempty → NO_MATCHES and no stale details; reset restoresall restrictions in oneaction. Search matchingothertrackselectsconsistentdetails and never controls music. Draft A survives selectingB/filteringAout/leavingLibrary; failedsave keepsdraft. Each6themes: openDeckselector→dismiss leavesenumunchanged; chooseanother→sidebar/settings agree→restart samechosenenum. No build/test result alone passes visualgate. Actualwindow/screenshots still required when capture available.

## Next sprint: minimal dirty-draft architecture

### Implementation callsite contract, 18 October 2026

`DesktopWorkspace` public signature is unchanged. It owns `remember { mutableStateMapOf<String, String>() }` for notes drafts, outside the selected page. `LaunchedEffect(tracks)` removes an entry only when the emitted persisted track has identical notes. Editing, selection and navigation never write notes to the database; Save retains the existing explicit `repository.editNotes` call. Errors and newer edits made during a save retain their drafts.

Private `DesktopLibraryPanel` replaces its `picked: TrackEntity?` argument with `selectedKey: String?` and derives the detail track from the tested view state. It receives `notesDrafts: Map<String, String>` and `onNotesDraft: (String, String) -> Unit` after the existing edit/poster callbacks. No external callsites exist. Filter and artist state stay panel-local; the query and notes drafts stay workspace-owned.

`DesktopTopBar` retains its public `onToggleTheme: () -> Unit` parameter for source compatibility; its meaning is opening the selector. `DesktopDeckScreen` owns `showThemeSelector`, routes this callback to opening it, and passes the original `onThemeSelected` persistence callback directly to the shared six-theme dialog. No persistence occurs on opening or dismissal.

Do not bundle all edit surfaces into this bounded fix. Introduce desktop-only draft owner per document, immutable baseline + working copy + save phase + pending navigation. Suggested `data class DesktopDraft<T>(val identity:String,val baseline:T,val value:T,val saving:Boolean=false,val error:String?=null)` with equality-baseddirty; `enum class DraftCloseDecision { SAVE, DISCARD, KEEP_EDITING }`; `suspend fun requestDraftClose(...)` resolves only after successfulsave/discard. UI close requests all route through one guard; loading must complete before establishing baseline, then must never overwrite dirtyworkingcopy. On failedsave pendingnavigation is cancelled and error remains inline. Save disables duplicate activation. Guard used for windowX, cancel, song selection, page navigation; no confirmation for pristine drafts. Track/editor/LRC document identity frozen duringsave; switchingplayback doesnotrebind editeddocument. Draft map fallback above is safe incremental protection, not completeexitprotection. Separate spec+cards needed for all callsites and applicationExit; no assertion of completion in thisbatch.
