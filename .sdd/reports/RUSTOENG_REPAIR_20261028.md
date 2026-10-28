# Rustoeng repair — 2026-10-28

Scope: provider and production-path tests only; main/canonical identical. No UI, router, Gradle, APK or committed work changed.

- Share widget itself terminates section before later editorial text, including when it has no heading; unknown free text outside lyric paragraphs fails closed.
- Provider-specific strict identity normalizes Unicode case/punctuation while retaining all edition and collaboration words. Search uses raw title; heading/article/reload checks use strict identity. The source's cross-script artist result with matching exact title is explicitly offered as a manual candidate; discovery validates the article artist matches the source search entry, and load revalidates candidate identity. No fuzzy cross-script result is auto-saved. Edition/feature mismatch is rejected, not auto-selected.
- HTTP 403/429 backoff now applies at every `readPage` network boundary, including candidate load and redirects. Lyric paragraphs with inline links retain words; known video metadata is excluded; ambiguous embedded video and unexpected text fail closed. HTML MIME and declared charset are checked, unsupported encoding/malformed UTF-8 rejected, Jsoup explicitly decodes UTF-8.
- Added production-path tests for sharing without heading, album/edition and collaborator mismatch, explicit cross-script candidate/revalidation and changed search/article artist, repeated load after 429, inline-linked stanza vs ambiguous iframe, unknown standalone text and mismatched charset. All synthetic lyric text; original fixtures unchanged. Tests are authored but not run by executor; independent test/review and live source acceptance remain pending.

| Path | Raw SHA256 (main = canonical) |
|---|---|
| `shared/src/jvmShared/kotlin/com/lirix/app/translations/RustoengTranslationProvider.kt` | `4af8f32d2285ccb160575561c19de057198eb57b79bf2c6246d65b97c73f2f11` |
| `desktop/src/test/kotlin/com/lirix/app/desktop/RustoengTranslationProviderTest.kt` | `0197e0dfb402d4a8446ec45f5c2ae3ae2dd353330241eab8dec1fd388184a278` |
| `desktop/src/test/resources/translations/rustoeng/search.html` | `e740697997765700b2f226a3d274378dee2c80f5d90aee811c69afca4f2c6c4f` |
| `desktop/src/test/resources/translations/rustoeng/5nizza.html` | `921da43f0b075603590854d053b9cb0c0e2127f4ea22566d89825dac41cc73fb` |
| `desktop/src/test/resources/translations/rustoeng/okudzhava.html` | `89884c4eeaa50f94eea3431da7ca16260f63e94a3dc4882f6b272508bccfb7e0` |
| `desktop/src/test/resources/translations/rustoeng/README.md` | `26699070af45cdaf7a9af76b488b588f648974a715c3a4f1d775cbc9bbc5dd3f` |
