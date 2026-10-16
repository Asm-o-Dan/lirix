# Wrapped / themed export independent review

Status: PASS for the limited theme/export integration after static re-review. No unresolved P0/P1/P2 finding in the reviewed changes.

Scope: EditorialPosterRenderer, PosterThemeSnapshot, PosterArtworkLoader, optional-theme adapters in ShareCardGenerator, actual Wrapped/NowPlaying UI export calls, ShareFormatDialog and ShareManager. No product code edited, no Gradle execution, no device or pixel verification performed by this reviewer.

## Resolved findings

- Square FULL third-track omission: EditorialPosterRenderer.kt:128 now halves explicit gaps in Square. Compared with the prior layout this saves 73px before the list; the three 120px rows now fit within bottom=968 for ordinary content. Stories retains its previous spacing. Overflow checks remain in place. Actual font metrics and long-text cases still require the root agent's generated PNG verification.
- Period/statistics mismatch: WrappedScreen.kt:105–106 now retains Pair(timeframe, stats) and exposes stats only when the selected period matches. Both the initial calculation (139–150) and periodic refresh (153–170) capture the period before calculating and publish the pair together on the composition coroutine. Export dialog requires nonnull matching stats (111), and the share control disables its click while matching stats are unavailable (332). Existing cancellation at withContext return prevents a superseded computation publishing stale data. The export callback freezes period/theme before artwork IO (116–119).

## Verified by inspection

- Actual UI passes nonnull PosterThemeSnapshot into both generators; legacy theme=null renderer remains separate.
- All themed renderer text/background/footer colors come from the immutable passed palette; renderer reads no Compose locals, DB, files or clock.
- Wrapped artwork map uses trackKey and null values preserve mapping when one artwork load fails.
- Artwork decode uses IO and bounded sample sizing; drawing uses Default; PNG work uses IO and chooser launch uses Main.
- Bitmaps are not recycled while rendering or compressing. No global renderer Bitmap/Canvas state is shared between requests.
- Text checks remaining space and clamps line count. Track rows reserve space before drawing, preventing footer overlap.
- Dialog retains error state and permits retry; cancellation is rethrown.

## Limits

This is static review, not visual acceptance. Root must inspect generated PNGs and report its own tests/device limits. Decoded/output bitmaps rely on GC after export; explicit ownership cleanup could reduce peak memory on repeated exports but no use-after-recycle issue was found. Generic platform fonts, full AnalyticsSnapshot timestamps/provenance, and composer/preview remain explicitly deferred scope.
