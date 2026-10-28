# Rustoeng provider: independent review — 2026-10-28

**FAIL: 2 P1 blockers**. Read-only source/fixtures review; no Gradle, device, live provider execution or integration review. Scope: `RustoengTranslationProvider.kt`, its eight tests and fixture structure, approved multilingual spec. Web page render independently checked 5’nizza and Okudzhava; full source access and detailed HTTP headers not revalidated by this reviewer.

## P1: sharing widget does not terminate the selected section

`shared/src/jvmShared/kotlin/com/lirix/app/translations/RustoengTranslationProvider.kt:124-136` walks every descendant after the chosen heading. It treats a known share widget as utility when skipping its descendants, but it only breaks on a *heading*. Minimal reproduction: `<h2>Soldier (Солдат)</h2><p>line</p><div class="sd-sharing"><p>share</p></div><p>unrelated editorial text</p>`. It returns `line\n\nunrelated editorial text`, silently storing outside-section content as the translation. Approved spec explicitly states known share widgets terminate the section. Fixtures `desktop/src/test/resources/translations/rustoeng/5nizza.html` and `okudzhava.html` always contain nested `<h3>Share this</h3>`, so current tests only pass because that heading happens to trigger break. Stop at the widget element itself, and test widget without a heading followed by unrelated paragraphs. Also reject/explicitly classify unknown section content rather than silently consuming it.

## P1: identity normalization silently discards edition/artist distinctions

`RustoengTranslationProvider.kt:172,181,189` uses `TranslationMetadata.title/titleKey`, and `:153,179` uses `artistKey`. The shared normalizers strip remaster / anniversary edition patterns and featured-artist metadata (`TranslationMetadata.kt:12,17-35`). Example: query `Солдат (2024 Remastered)` by 5nizza normalizes to `Солдат`; the plain `Soldier (Солдат)` section can return `Found` and be auto-saved, despite the approved requirement to preserve versions and never treat alternative editions as verified exact matches. Similarly, featured collaborators can be erased from artist identity before automatic matching. A provider-specific stricter identity check must preserve version-bearing fields, or surface the ambiguous result as a candidate requiring confirmation. Add negative production-path tests for remaster/edition and collaborator mismatch.

## P2: alias mismatch appears as definitive Missing

`RustoengTranslationProvider.kt:150-159,174-186` filters search results by exact artistKey before loading any page. Given a result headed `Kipelov` for requested `Кипелов`, it returns `Missing` without allowing candidate confirmation. This is not an unsafe auto-match, but it prevents the task’s important Russian→English use case for players and sources using different scripts. The spec explicitly distinguishes ambiguous Cyrillic/Latin aliases and requires candidate confirmation or explicit verified aliases; never guess equivalence. Add an actual representative alias fixture, bound and validate result pages, surface uncertain candidates only with manual confirmation and preserve exact artist/title checking on re-load. If the intended first iteration deliberately declines alias support, return a distinct honest unsupported/ambiguous outcome rather than presenting it as confirmed absence.

## P2: per-source 403/429 backoff bypassed by load

`RustoengTranslationProvider.kt:163-171,217-219` sets blockedUntil for any 403/429 but checks it only in `find`. Calling `load` repeatedly after a 403/429 still sends requests; this violates the declared provider backoff and can hammer the source from candidate previews. Check the same backoff at the network boundary, test 429 plus repeated load. Preserve structured failure and cancellation.

## P2: silent partial lyrics from a linked paragraph

`RustoengTranslationProvider.kt:134` silently omits any `<p>` containing an anchor/iframe/video, including a genuine lyric stanza with an inline link. If another paragraph exists, it returns `Found` containing only the remainder without warning. Fixture uses link-only `Video:` so it doesn't detect this. Separate known non-lyric media metadata from lyric text or fail closed on ambiguous mixed content; add a stanza-with-inline-link structural fixture and verify no silent truncation.

## Checks and limits

HTTPS and host allowlist, userinfo/port/path/query validation, bounded body and redirect count are visible in `:42-49,194-239`; redirects are revalidated each hop. Reference carries strict canonical Base64URL UTF-8 JSON artist/title pair (`:52-87`), and load compares both fields through shared normalization (`:118-122`), subject to edition/artist collision above. Sequential page requests have individual 8s connect/12s read limits but no overall limit inside provider; integration must enforce the approved external provider budget and preserve outer cancellation. `Jsoup.parse(..., null, ...)` relies on HTML sniffing/fallback rather than HTTP declared charset; test content type/charset mismatch or fail closed if supported sources declare non-UTF-8. Response size, timeouts, 429, cancellation, valid same-host redirects and actual network are not exercised by these eight tests. App/desktop consumers are not wired by this task. Robots/licence/terms and device behavior remain separate; no claim of release readiness.
