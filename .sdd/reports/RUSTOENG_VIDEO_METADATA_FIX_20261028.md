# Rustoeng: video metadata correction — 2026-10-28

The parser now excludes the specifically observed linked paragraph `Video of English translation:` in addition to existing recognized `Video:`, `YouTube:` and `Watch video:` labels. The 5nizza fixture preserves this actual structural label with a synthetic URL and synthetic lyric text. The existing end-to-end provider test asserts exact translated lyric and absence of metadata label/URL; the existing separate `linkedLyricsArePreservedButAmbiguousEmbeddedMediaFailsClosed` test covers genuine inline-linked lyric text. Main and canonical files match by SHA256.

Gradle, APK and runtime not run by executor; independent review and tests still needed. The rule is deliberately narrow: unknown media labels require further source evidence; it does not suppress arbitrary linked lyric paragraphs.

- `shared/src/jvmShared/kotlin/com/lirix/app/translations/RustoengTranslationProvider.kt`: `2b36394e564060060002fa4d5302f15e306189ef5d40ef5a3217192f20f99abc`
- `desktop/src/test/resources/translations/rustoeng/5nizza.html`: `61544ca4e224d3fbd5ab179f48fccc65745c92866e47244820f7ac2db5ae40d5`
- `desktop/src/test/kotlin/com/lirix/app/desktop/RustoengTranslationProviderTest.kt`: `a4fbb549e84531d1317f306af1c86674dfb785c976463cbc13f6ba35069a11e9`
