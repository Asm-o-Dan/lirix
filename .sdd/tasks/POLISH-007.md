# POLISH-007: KaraokeStudioEngine.exportLrc

Contract: ../specs/android-ui/player_studio_polish_v1.md

Retain exact millisecond timestamps across studio export and reimport; preserve occurrences/text and old import support.

Tests: whole Sakura/Molihua fixtures, distinct non-centisecond stamps, playback-parser roundtrip and overflow rejection. Evidence is recorded in the polish report.
