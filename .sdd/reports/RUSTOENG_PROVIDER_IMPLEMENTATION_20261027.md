# Rustoeng provider — 2026-10-27

Scoped provider/parser only: `RustoengTranslationProvider.find(title, artist, refresh)` / `load(sectionUrl)`; coordinator routing, LyricsTranslate and TranslationPanel intentionally not edited. The provider uses a verified bounded WordPress search, restricts results to `main#main article header.entry-header h1.entry-title a`, ignores unrelated navigation and validates page artist + section. h2/h3 delimiters, unknown headings and Share widget boundaries; no inferred original; target en and truthful source `Rustoeng`. Unique section reference is canonical `#lirix-v1=` base64url UTF-8 JSON pair `[artist,title]`, validated with path/host/size, metadata and canonical encoding. Reload revalidates both fields; separate sections have distinct references. HTTPS only, bounded response/redirect, interruptible IO and 403/429 backoff. The code recognizes only exact normalized metadata; Cyrillic/Latin alias mismatch is an honest no-match (not fuzzy auto-save).

Observed live DOM via ordinary read-only request: search and 5nizza/Okudzhava responses 200 on 2026-10-27. Fixtures preserve minimal real metadata/structural layout with synthetic lyric text, not full copyrighted lyrics. Eight unit tests exercise actual provider fake transport, 5nizza and multi-section parse, wrong artist/title, reread artist/title changes, malformed references, changed markup, unknown heading and duplicate identity, exact status/redirect/backoff. No Gradle or test run by author; no runtime source or permission/licence conclusion, no Android or UI validation. `refresh` accepted for API compatibility; provider has no negative-result cache to bypass.

Raw SHA256 identical in main/canonical:

| File | SHA256 |
|---|---|
| `shared/src/jvmShared/kotlin/com/lirix/app/translations/RustoengTranslationProvider.kt` | `332633b1211212c3e785150bef6d9c9613411d7a35140c0007c8fee3fd683ce0` |
| `desktop/src/test/kotlin/com/lirix/app/desktop/RustoengTranslationProviderTest.kt` | `df144449d669c66e163be5d29c1a516ea5b3599954947e786e8ecd26d92d114e` |
| `desktop/src/test/resources/translations/rustoeng/search.html` | `e740697997765700b2f226a3d274378dee2c80f5d90aee811c69afca4f2c6c4f` |
| `desktop/src/test/resources/translations/rustoeng/5nizza.html` | `921da43f0b075603590854d053b9cb0c0e2127f4ea22566d89825dac41cc73fb` |
| `desktop/src/test/resources/translations/rustoeng/okudzhava.html` | `89884c4eeaa50f94eea3431da7ca16260f63e94a3dc4882f6b272508bccfb7e0` |
| `desktop/src/test/resources/translations/rustoeng/README.md` | `26699070af45cdaf7a9af76b488b588f648974a715c3a4f1d775cbc9bbc5dd3f` |
