# Wrapped / poster themes — implemented phase 1

Status: IMPLEMENTED; static review PASS; native bitmap QA PASS on 2026-10-16.
Owner: Codex, following the user's switch to direct implementation (ADR-048).

## Contract

The actual Wrapped and Now Playing export flows capture an immutable PosterThemeSnapshot from LirixThemeTokens before suspension. The renderer accepts ARGB tokens and decoded bitmaps, without reading Compose locals or files. Background, surface, primary/secondary text, border and accent follow the selected theme. Legacy generator overloads remain available for existing callers.

Wrapped export captures a matching timeframe/statistics pair. Until the selected period's calculation finishes, export is disabled. Artwork is keyed by trackKey; a missing bitmap must not move a later cover into another track's row. Local image decoding runs on IO, bitmap rendering on Default, PNG writing on IO, and the chooser starts on Main.

Formats: 1080×1920 and 1080×1080. Layout reserves a footer and bounds text with ellipsis. FULL shows at most five tracks in Stories and three in Square. Native Canvas tests cover all six themes, missing artwork, long strings, deterministic rendering and empty data. Preview fixtures use synthetic listening data.

Labels distinguish launches from listening duration, and saved lyrics from actual lyric views. MONTH means the last 30 days. No invented year is printed in the footer.

## Scope remaining from the broader proposal

Bundled Android editorial fonts, a poster preview/editor, duration attribution across time windows, selection of poster content, and migration away from the legacy renderer are separate work. Current serif/sans use Android system typefaces. This phase does not claim to correct the entire analytics model.

## Evidence

- ThemedPosterRenderTest: 3 tests PASS; 6 themes × 2 formats × 2 export types.
- Root visually inspected Paper Ink square Wrapped, Graphite Stories track, Pastel square Wrapped, and Paper Ink long text.
- Device 2440cbe2: 1.4.0/code17 installed preserving data; actual Wrapped poster opened in the user's image viewer. All five artwork rows and footer fit.
- Independent static review: .sdd/reports/WRAPPED_POSTER_THEME_REVIEW.md.
