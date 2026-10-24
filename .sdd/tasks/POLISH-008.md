# POLISH-008: LrcSyncEngine.parseLrcLinesStrict

Contract: ../specs/android-ui/player_studio_polish_v1.md

Accept representable 100+ minute LRC timestamps; reject unrepresentable minutes and arithmetic overflow. Preserve legacy fraction parsing.

Tests: whole Sakura/Molihua fixtures, distinct non-centisecond stamps, playback-parser roundtrip and overflow rejection. Evidence is recorded in the polish report.
