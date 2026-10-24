# Player Gold repair — approved bounded addendum

- `MusicDao.toggleFavorite(track)` runs one Room transaction. Insert absent row with IGNORE (never replace accumulated data), invert actual stored flag, return stored value. Existing title/counts/lyrics/notes must survive. Repeated toggles use database state, not stale captured UI entity.
- Player live and selected-track presentation merge the observed Room entity for favorite/notes/lyrics; live title/art/source still follow media session. Heart enabled while no write is pending and shows stored state. Failure produces existing error feedback; no false optimistic success.
- AppScaffold must consume inner safeDrawing padding so children apply only remaining insets. No removal of status/navigation/cutout safety. Verify portrait, landscape, large font.
- Chord parser must recognize chord-only structural wrappers and section labels without turning ordinary lyric/prose words into chords. Transposition preserves suffixes, wrappers and slash bass; signed semitone offset applies to every recognized chord. Root delegates the helper separately.

Device evidence: build/repairs-device/chords-before.xml and chords-plus.xml; Intro: Am (Em) F (C) }x2 unchanged while selector Em→Fm. Heart remained outline because active TrackEntity construction omitted stored isFavorite. Parent Scaffold padding and child statusBarsPadding both applied the same unconsumed inset.
