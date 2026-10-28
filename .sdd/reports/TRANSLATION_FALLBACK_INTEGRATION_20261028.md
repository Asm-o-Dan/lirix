# English translation fallback integration — 2026-10-28

Prepared, not yet compiled or run; no Gradle/APK. Rustoeng source itself belongs to another executor.

## Scope

- `SongTranslationProviders`: route Rustoeng host `load` and exact English searches with LyricsTranslate-first priority. A unique English LT Found or candidates return unchanged without calling fallback. LT clean miss uses Rustoeng normally. Failed/timeout LT plus valid Rustoeng returns partial Candidates with `LyricsTranslate` unavailable and never auto-saves; if fallback also fails/misses return safe error. Every Found is checked for `en`, errors are not displayed with raw exception text. Each provider has a 14s bounded local `withTimeoutOrNull` within the existing 35s coordinator, 28s max budget not including noncooperative I/O. Outer cancellation propagates; an inner source TimeoutCancellationException is treated as a source failure only if calling context is still active. Constructor exposes trailing default network-search lambdas and timeout for production-router behavioral tests; existing constructor usages/defaults remain compatible. Dirty main Lyrsense routing retained as is; canonical based on HEAD without foreign Lyrsense.
- `AutomaticSongTranslations`: backward-compatible default-empty unavailable source lists on result/state, bounded fixed allowlist, pass through candidate state. Replaced own global timeout with `withTimeoutOrNull`: own 35s expiry becomes Failed, parent cancellation propagates and clears state. Room DAO/saveAutomaticIfEmpty untouched.
- `TranslationPanel` displays localized RU/EN incomplete-source warning before candidates; labels on source names come only from fixed allowlist. Error text uses known safe localized failure. Prior unaligned UI changes and dirty main `originalLineContent` hook retained.
- `UiMessageCatalog`: add only two RU/EN keys for partial warning and safe failure.

## Behavioral tests (unrun)

`EnglishTranslationFallbackTest`: production router fakes no-network primary/fallback; LT exact auto-save and zero fallback, LT candidates, LT Missing → Rustoeng Found/candidates/Missing/error, LT failure → partial/no automatic write, fallback candidates, invalid language, both errors/Missing distinction, fixed source allowlist, source timeout/inner source timeout vs caller cancellation, actual Room manual-write race and cache preference. Fake injections cover routing, not provider parsers; separate real-source-derived provider tests belong to provider owner. 8 JUnit methods (including real Room).

`PartialTranslationPanelTest`: actual ComposeScene semantics RU/EN showing partial warning, candidate preview, disabled save button and no save before explicit checkbox and save, and actual saved document. One JUnit method loops both languages; no claim that compilation/rendering has passed or phone behavior was verified.

## Raw-byte SHA256 freeze

| File | Main | Canonical |
|---|---|---|
| `shared/src/jvmShared/kotlin/com/lirix/app/translations/SongTranslationProviders.kt` | `62af468aae3266a46c07f6a55d0155947111e4d39aab12f9d6056fd52f122232` | `8869a1475686fcc425b8b5e1bd0705c8a34073a8409f651c1b9156414c3d6b19` |
| `shared/src/commonMain/kotlin/com/lirix/app/translations/AutomaticSongTranslations.kt` | `071130af153d8c0624a3c5566cc906cb6568df56e0812dea503d8402cc9321d5` | same |
| `shared/src/commonMain/kotlin/com/lirix/app/translations/TranslationPanel.kt` | `2aa7da4595ab8410caee4280ed341a1b1c931609a45c9f11cbf6ec92d8a7fa81` | `4ae03f6f7da43ab3e66cf3eff97dbda07cc34bfa983741491e9a8ba4860ee871` |
| `shared/src/commonMain/kotlin/com/lirix/app/ui/i18n/UiMessageCatalog.kt` | `51c668086cfa0160166a2dd71f253f990225dc793551fed5845c9a7db8368756` | same |
| `desktop/src/test/kotlin/com/lirix/app/desktop/EnglishTranslationFallbackTest.kt` | `4398ddcdad89f860ba17f3fd1234140bcac8716e8ea72f0232a11068fbf74fde` | same |
| `desktop/src/test/kotlin/com/lirix/app/desktop/PartialTranslationPanelTest.kt` | `32813204e74f01cf325c49b0ba798fea6ddffe225639688637cb69a8f968ad65` | same |

Do not overwrite main provider/TranslationPanel with canonical blobs; main contains unrelated Lyrsense and reading/i18n edits. Canonical UI is actual HEAD + approved unaligned and partial warning changes, not the initial contaminated canonical copy. Recompute hashes after further coordination before staging.

## UI review follow-up — 2026-10-28

Canonical partial-result flow now localizes candidate instruction, preview, checkbox description, confirmation text, save button and candidate preview errors using existing RU/EN catalog entries. Main preserves the unrelated reading hook and renders source/author and the open-source action for every non-compact document, including cached/automatic results. Candidate loading errors no longer expose exception text.

The Compose semantic test now verifies exact localized labels, disabled and enabled save states, persistence after confirmation, source/author attribution and opening its URL in RU and EN. Test execution remains pending parent build; no Gradle, APK, device, or live-source check was run here.

Frozen SHA-256: main TranslationPanel `12041044e6c990a1b5439ca4f26153a6d0d927972d9029485fcb7455e3656474`; canonical TranslationPanel `d91e24c58ad10fce683caa06c9e35830c5dc9adfa20b75799d374fb52767c65e`; PartialTranslationPanelTest in both trees `9d7d162bb84576513bf816ba1e605399eeb1fc475351cd8a8bac623c154e7afd`.

## Candidate identity follow-up — 2026-10-28

Before displaying a candidate preview, the loaded artist and title must equal the selected source candidate after case and whitespace normalization. A stale or altered page produces a localized error and cannot be saved. The check deliberately compares the selected source metadata, not the user's track metadata, so cross-script manually selected candidates remain possible. Compact previews display artist and title before confirmation. Exact matching may conservatively reject a provider whose search and detail metadata differ in punctuation or formatting; this remains a known trade-off pending real-source coverage.

Compose test covers changed source in compact/wide views, Japanese candidate selected for a Latin query, confirmation and attribution, and searches all semantic nodes for the checkbox description with getOrNull. No Gradle/APK/device run was performed by author.

Frozen SHA-256: main panel `16cd067f5aac83fb4c5cd77a0f0873376d9c5c033462f19e7414a37f94d4b87d`; canonical panel `ff33be2c1c0e9213a7eebf7bee2bd30489f9c03ab6f60ae09eb91b09f961f84d`; catalog `e4d0966a6096323dd98c9318cb3d867aec02d0fa84a8a46c2cb47c45095098ca`; test `b99107e213fa941326b486b17b30ebb457a6a55df555dfe897a737d921543a82`.

## Source alias follow-up — 2026-10-28

LyricsTranslate search may include a Latin parenthesis omitted by the parsed detail title. Since the current document model does not preserve independent evidence of transliteration, this case is explicitly treated as uncertain rather than an identity match: the source search title and detail title are both displayed and the user must affirm the version before saving. Other sources do not receive this exception. The artist must still match, the unchanged base must contain Cyrillic, and obvious edition qualifiers (including Remastered) are rejected. This is a conservative user-confirmed choice, not evidence the transcription is correct; other Latin qualifiers can be ambiguous and remain visible for user judgment.

UI tests exercise the real-derived Kipelov metadata in both locales and compact/wide views, plus changed artist and Remastered negatives. Not run by author. Frozen hashes: main panel `db13319e69daae06f14525c211fc3aeb865de2b083fdd04d9b0a4e0bc939561e`, canonical panel `c9289299532f587fe83cd75e00579622b03cc68412420159d993c92f8bc221ed`, catalog `e30802b54f5b5e0b276c56919fb6a2ccc6a09191d7ebcac65a069421294da491`, test `fd8b923eef8d6988f1b65f273a9b582e82b82479f6eee214b4486baaca2784f1`.
