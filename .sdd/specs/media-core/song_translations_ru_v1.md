# Song translations: Russian first

Date: 2026-10-18. Status: DESIGN PROPOSAL, not implemented. User requested an integration design; this does not silently add schema changes to the desktop-readiness patch.

## Source evidence

User example: https://www.amalgama-lab.com/songs/f/falling_in_reverse/joseph.html

The public page was fetched successfully with HTTP 200 for inspection. Content-Type and HTML meta specify windows-1251. Its song body has 70 `div.string_container` rows in this inspected revision; each contains `.original` and `.translate`. Song headings are `h2.original` and `h2.translate`, and the translated heading credits JaneTheDestroyer with an author link. Preserve this metadata; do not treat all `.translate` elements on the page as song lines. The site's displayed search form submits to Yandex site search; it is not evidence of a public Amalgama API. HTML inspection file is an ignored build artifact, not a fixture committed with copyrighted lyrics.

## Product shape

Translations are a separate optional content layer, available on both Android and PC. They never overwrite original plain lyrics, LRC, chords or user notes.

Within the text view offer Original / Russian / Together. On a wide PC, Together can use paired columns; on phone or a narrow panel, stack the original and its translation in each paired block. Preserve verse headers, empty section separators, footnotes and translator credit. Changing the display mode must not refetch or alter playback.

Karaoke keeps original LRC as its clock. A Russian subtitle may appear under a line only when its pairing with that exact version of the original is verified. A full-page translation without a verified LRC mapping remains readable as static text. It must not get fabricated timestamps or word timing. HUD translation is separately optional and off by default; it must obey measured active-block geometry, not make an already crowded overlay unreadable.

## First usable increment

1. Current track → Add translation → paste an Amalgama song URL.
2. Load a preview with detected artist/title, translated title, credited author, original/translation pairs and source link.
3. Confirm the song and selected variant, then save for offline use. A mismatch is shown before attachment, not accepted solely because the title matches.
4. Display the three content modes. Permit changing the source or a local corrected translation explicitly; user corrections outrank refresh results.

No search engine API, paid model or machine translation service is required for this increment. Later, Find translation can resolve candidates through a separately tested source discovery adapter. Start with source artist/song catalogs where reliable; do not build an invented REST API or blindly guess every URL from artist/title slugs. External search, if chosen later, is its own visible fallback.

## Shared architecture

Common module owns provider contracts, translation DTOs, storage schema, matching/alignment and UI state. Android and desktop share those semantics. HTTP/HTML details live in the Amalgama adapter, using an actual compatible JVM parser on the existing Android/desktop targets; platform UI and file/share entry points remain separate.

Proposed types:

- `TranslationCandidate`: provider id, canonical URL, artist/title, target language, variant identity and credited author when known.
- `TranslationBlock`: stable source order, original text, translated text, kind (lyric/section/note), optional source note reference. Repetition is retained, not deduplicated.
- `SongTranslation`: local id, track key, target language `ru`, source metadata, ordered blocks, fetched/edited timestamps, original-content fingerprint and local-edit flag.
- `TranslationAlignment`: translation id + fingerprint of the exact LRC text/version + ordered links from LRC indices to source block ranges + status (unmapped/partial/verified/user-confirmed).
- `TranslationProvider`: candidate discovery and document loading are separate suspend operations. A direct URL load works without discovery. Results distinguish not found, unavailable source, malformed document and a document requiring manual selection.

Use a dedicated translations table and alignment storage rather than another field in `lyrics_cache`. Select at most one preferred variant per track/language while retaining provenance; a local edit must not silently erase the source document. Schema migration must retain existing history and lyrics on both platforms. Include translations, provenance, preferred variant and valid alignment versioning in the future archive format; do not mix them into the current unfinished archive version without an explicit version change.

## Pairing and karaoke

Amalgama's original/translation pairs are not automatically the same line division as LRCLIB's LRC. Normalize Unicode punctuation, case and whitespace for matching, retaining meaningful words/numbers. Headers/notes are typed blocks rather than lyric content. Match occurrences in order; repeated chorus lines cannot all be assigned to the first match.

The first alignment implementation accepts exact normalized monotonic matches. Different verse lengths, split/merged lines, repeated-section shorthand or a different live/remastered version produce partial/unmapped results, with an optional manual pairing workflow. Never pair by list index merely because the two lists have similar lengths. When the original/LRC fingerprint changes, invalidate the old alignment. For an unmapped active line hide its Russian subtitle rather than leave the previous line's translation visible. Translation display follows the original line time as a whole; it is not word-level singing synchronization.

## Provider contract

- Parse the paired song rows and dedicated headings, not the entire page's text. A row missing one side is retained as an explicit incomplete block; it cannot shift every later pair.
- Decode the response's declared charset (windows-1251 in the example) before parsing; preserve Cyrillic, entities, breaks, section headings and translator metadata.
- Initial direct URL adapter accepts HTTPS Amalgama song pages and validates redirects/document identity. Do not execute page scripts or accept an error/search page as a song because HTTP status is 200.
- Bounded timeout/body size, cancellation on track/request change, source-error state and cache-before-network on repeated viewing. Concurrent duplicate loads coalesce; do not crawl a catalog in the background for every playback tick.
- Cache successful selected documents locally; refresh is explicit. Rejected variants stay rejected for that track/language. A failed refresh does not discard the last saved translation or replace a local edit.
- Source/translator attribution and an Open source action remain visible. If the document cannot be read normally, expose that state; do not add access-barrier bypasses.

## Verification before implementation acceptance

Use synthetic HTML fixtures (not full copyrighted songs) for paired rows, missing sides, section boundaries, footnotes, alternate authors, entities and windows-1251 decoding. Validate URL/redirect/error-page handling and cancellation without live network tests.

Use synthetic LRC/source documents for exact and repeated lines, mismatched versions, split/merged paragraphs, no timestamps, partial alignment and invalidation after editing. Test that translations never mutate original lyrics/chords and local edits survive refresh. Database migration/reopen and archive versioning are separate required tests.

Render wide/narrow desktop and phone text states, long Russian lines and karaoke with/without a verified subtitle. Verify a missing translation is an honest empty state; a missing mapping must not look like accurate synchronization. The first provider is replaceable; further languages reuse the same model rather than adding language-specific columns.

## Delivery order

1. Finish current desktop readability/data-integrity patch.
2. Shared translation model + safe storage migration + Amalgama URL parser + explicit preview/import on PC and phone.
3. Shared matching and guarded bilingual karaoke.
4. Automatic discovery, manual alignment refinement and optional translated HUD.

This proposal needs a separate implementation scope after the current readiness patch. It does not claim that a translation feature, live provider or complete rights/access policy has already been shipped.
