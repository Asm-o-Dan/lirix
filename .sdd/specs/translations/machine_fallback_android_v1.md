# Android machine translation fallback v1

2026-11-08; Level 2. Implements the previously approved machine-fallback section of intake/LIRIX_LIBRARY_TEACH_20261107.md. No database schema migration or replacement of song_translations.

## Product contract
- Optional on-device ML Kit translation, disabled initially. Enable in the existing translation settings sheet. UI explains model downloads over Wi-Fi and approximate meaning, rather than claiming a published literary translation.
- Only Missing from published-source lookup permits fallback. Failed, Searching, Candidates never trigger it. Existing published/manual document and pending user preview always win. Changing original/track/language cancels the previous UI request and clears its result.
- Model download is an explicit action, Wi-Fi only. Without models show a download action; unsupported/undetected original language, absent original, same source/target, download errors and cancellation have distinct outcomes. Keep original visible, retry errors explicitly.
- Detect original language with bundled ML Kit language-id 17.0.6 (works without a network). Translate using translate 17.0.3. Separate translation client per bounded request, closed after operation; detect/translate off UI thread; cancellation and timeout supported. No lyric logging, no cloud text-translation endpoint. SDK/model acquisition may contact Google.
- Preserve source lines and stanza breaks. Translate each nonempty line separately with a within-request memo for repeated lines; do not invent karaoke timing. Maximum 50,000 characters and 1,000 lines; timeout bounds decoding/download/translation.

## Interfaces and storage
- Shared optional MachineTranslationFallback interface injected into TranslationPanel, default null for unchanged Desktop behavior. StateFlow opt-in; suspend translate immutable request (stable trackKey, original, title, artist, target), with explicit download flag. Results: translated document, model required, same language, unknown/unsupported language; operational failures propagate to retry UI.
- Machine document uses existing SongTranslation display DTO with empty sourceUrl, an explicit machine sourceName, and a warning; never saved to SongTranslationDao. No fake URL, manual-save path hidden for machine results. Attribution view must not expose an open-source-URL action for empty URLs.
- Android private cache directory (excluded from backup) stores results atomically. Key: SHA-256 over trackKey, original hash, source, target, integration/model-cache version. ML Kit does not expose an exact model revision; version denotes SDK/integration revision, not a claimed neural model ID. Store and validate metadata and original blocks. Different original invalidates by key; ignore corrupt cache and regenerate. Bound by 128 files/20 MiB with oldest eviction. Cache eviction may require regeneration; cache is not a user archive.
- Opt-in in lirix_translations shared preferences (already on backup allowlist). No database writes by machine fallback, so later human translations remain eligible for saveAutomaticIfEmpty. Existing stored human documents retain their format.

## Verification
- Shared tests: policy gating Missing vs failure/candidates, human/manual precedence, source-line integrity, changed inputs and cancellation. Headless Compose: opt-in, model-needed action, visible machine provenance, human result replaces machine, original remains visible after failure, no source URL for machine.
- JVM/Robolectric with external ML boundary faked: cache hit avoids inference, source/target/content/version separation, corruption, bounded cache, no effect on human DAO. Real ML adapter compile, official API check.
- Android models on device, Wi-Fi/offline, actual EN→RU and RU→EN linguistic quality and cancellation: NOT RUN until isolated device/emulator available. Never run connected tests against personal phone or uninstall its app.
- Plan review before implementation and independent final review required.

## Plan-review corrections
- Lyrsense currently converts backoff, null transport, timeout and exceptions into Missing. Correct this before wiring fallback: only validated empty search is Missing; operational failure propagates to AutomaticTranslationState.Failed when no other source succeeds. Changed/uncertain identity is not absence. Add realistic fake-transport tests.
- Follow ML Kit translation-terms and Cloud Translation attribution: official unmodified powered-by-Google-Translate badge adjacent to machine results; translation action attributed to Google; help in panel includes Google Translate explanation/link and required disclaimer. Human providers keep their own attribution, no fake source URL.
- Coroutine cancellation prohibits stale publication; ML Kit SDK model download may finish in background, so do not promise download cancellation. Ensure clients release safely.
- Cache hit test asserts no translate call; language detection can still be necessary for the source-specific key.

## Revision 2 — privacy decision, supersedes ML Kit design (2026-11-08)
User explicitly chose Zero Telemetry over ML Kit. Remove both ML Kit dependencies, initializer, Google-specific artwork/branding and ML backend. Previous 47 passing fake-boundary tests do not approve shipping the rejected SDK.

Replacement plan:
- ONNX Runtime Android pinned 1.23.2, whose own Privacy.md restricts telemetry to Windows. Call public setTelemetry(false) as defense-in-depth, prohibit silent upgrades. No ONNX Windows runtime in production; native tests must disable telemetry before creating sessions.
- OPUS-MT EN→RU and RU→EN first; published-source languages remain unchanged. Other machine pairs explicitly unsupported, never guessed. Detect language through pure-Java Optimaize 0.6 bundled profiles (no network API).
- Quantized Marian encoder/decoder/decoder-with-past graphs from HF Zweiarm/opus-mt-onnx-int8 revision 32a5add3077acbe625776e56cf2eb0f7aa621732, individually pinned byte sizes and SHA256. Do not use undocumented tokenizer.bin. Use standard source.spm/target.spm/vocab.json from pinned Helsinki-NLP source repos (EN→RU bb09c99d180016eac6819df3dae68edb1690fdee; RU→EN fbd6dc73284f95536648512cc21d57f19191961a).
- Official SentencePiece 0.2.0 built locally as small JNI library through pinned source archive + SHA256, Android NDK already installed. No custom tokenizer approximation. Encode to string pieces → Marian vocabulary IDs + EOS; generation uses exact graph input metadata and KV cache; decode vocabulary string pieces using target SentencePiece. Guard token limits, EOS termination, resource ownership and cancellation between native calls. No silent truncation.
- Explicit Wi-Fi-only download with byte progress and advertised ~231MB per direction; bounded HTTPS redirects restricted to HF/CDN hosts, pinned hash verification before atomic install, partial/corrupt files are never treated as ready. Network drops/cancellation leave no installed partial package. Cache and models under private non-backed-up directories; no database schema changes.
- Separate notices: upstream EN→RU Apache-2.0, RU→EN CC-BY4, quantized conversion repo CC-BY4; record ONNX export/int8 modifications. Runtime MIT, SentencePiece Apache-2.0. UI attribution is OPUS-MT/Helsinki, no fake human-source URL.
- Keep previously reviewed opt-in/Missing-only/first-DAO-read/human priority/cancellation/cache contract. Adapt download metadata to directional packages; timeout for large package download is distinct from inference.
- Verification: real tokenizer vs pinned Python SentencePiece oracle and CPU ONNX model fixtures for EN→RU/RU→EN where possible, Android native compile, fake transport/model download corruption/cancellation/cache tests, existing UI gates. Actual phone memory/latency and native Android execution remain NOT RUN unless isolated test environment available.

Review required before implementing replacement engine; removal of rejected SDK is already explicitly authorized.

## Revision 3 — user-approved two distributions (2026-11-08)
User approved two APK variants so each person can choose memory/convenience or zero engine telemetry. This supersedes the single-private distribution decision, not the private variant's privacy guarantee.
- Gradle translation dimension: private (recommended) and compact. Same applicationId, versionCode and signing configuration, so an explicit manual switch is a replacement installation retaining the existing data directory. No uninstall/migration. Actual Android replacement remains a device acceptance gate.
- private source set contains only ORT1.23.2/Optimaize/SentencePiece backend; compact source set alone contains ML Kit libraries, initializer and official brand assets. Common main contains interfaces/cache/UI only; platform factory has same FQCN in separate source sets. Dependency graph and merged manifests must prove isolation.
- Shared provider metadata drives name, download hint, source attribution, help/telemetry disclosure and cache version; no misleading zero-telemetry claim for compact. Only private is labelled zero engine telemetry.
- Update assets exact names lirix-vVERSION-private.apk / lirix-vVERSION-compact.apk. Selection uses current build variant, never guesses a generic legacy APK or the other variant. Installed/candidate manifest metadata also must match, besides package/signature/version checks. Releases missing current variant are skipped. UI states current variant. Intentional switching is manual and explicit.
- CI builds/tests both flavours, release workflow publishes both named artifacts without publishing a release in this task. README describes the two choices and model storage costs. Existing CI historic task aliases updated to qualified variant tasks.
- Verification includes malicious/misnamed opposite-variant metadata, both dependency graphs/manifests, both build compilations, model/cache/UI tests. No personal phone installation/uninstallation.
