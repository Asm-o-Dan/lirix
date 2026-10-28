# Rustoeng final review — 2026-10-28

**FAIL: real-site video metadata is incorrectly included in a translated lyric.** Read-only review of repaired main and canonical provider/tests; no Gradle, APK or production edits. Tested actual site metadata-only with ordinary HTTPS GET; no lyric text was logged or persisted. Main/canonical provider SHA256 `4af8f32d2285ccb160575561c19de057198eb57b79bf2c6246d65b97c73f2f11`, tests `0197e0dfb402d4a8446ec45f5c2ae3ae2dd353330241eab8dec1fd388184a278`.

## P1: the positive real source includes the video URL in the translation

`shared/src/jvmShared/kotlin/com/lirix/app/translations/RustoengTranslationProvider.kt:158-163` skips linked video metadata only if paragraph text matches `^(video|youtube|watch video)\\s*:`. On 2026-10-28 an ordinary GET of https://rustoeng.com/5nizza/ returned 200, `text/html; charset=UTF-8`. Inside the actual `main#main article .entry-content` the paragraph after the song heading and video figure is `<p class="wp-block-paragraph"><strong>Video of English translation:</strong> <a href="https://www.youtube.com/watch?v=8WH6NckqRWE">...</a></p>`. This is not matched by the regex, passes the iframe/video-only rejection, and is appended to `pieces` via `wholeText`. Consequently `find("Солдат", "5nizza")` can return Found with a video description and URL prepended to the translation, contrary to content purity. The test fixture `desktop/src/test/resources/translations/rustoeng/5nizza.html` instead simplifies the real label to `Video:`, so existing tests miss this.

Fix: recognize only verified video metadata variants or fail closed for uncertain media paragraphs, while preserving genuine inline-linked lyric stanzas; add a structural fixture derived from the exact label and assert the URL and label are absent and all synthetic lyric lines remain. Avoid broadly dropping linked lyric paragraphs.

## Checks and limitations

- Prior share-widget P1 fixed: `RustoengTranslationProvider.kt:145-146` now breaks on the actual widget, with test `sharingWidgetWithoutHeadingTerminatesSectionBeforeFollowingEditorialText` in `RustoengTranslationProviderTest.kt:128`.
- Prior edition/collaborator P1 fixed for disclosed cases: strict normalization retains version/collaborator words (`:40-42,213`), and test `:139-147` prevents remaster/feat from auto-matching. No UI/runtime tests run.
- Cross-script artist uncertainty is Candidates, with article search identity revalidation (`:180-185,205-220`), tested at `:150-160`. It deliberately does not assert that transliteration proves identity.
- Network-boundary blockedUntil check now covers find and load (`:234-252`; test `:163-170`); URL host/scheme/path restrictions, typed fragment and redirect revalidation remain in place. Declared HTML MIME/charset and UTF-8 decoder are validated (`:255-275`), but test covers unsupported declared charset only, not malformed bytes. Overall timeout/cancellation belongs to pending router integration.
- Linked lyric paragraph words are retained (`:158-163`, test `:173-180`); figure/video metadata may be skipped only when identified. Unexpected iframe is rejected, but standalone iframe outside known figure with no own text is not categorically rejected; exact source sections need runtime fixtures for new markup.
- Live source metadata: `5nizza` artist header and song h2 exist, Okudzhava has h2 then h3; search DOM includes result article plus a generic Welcome article. These observations are time-sensitive and do not prove rights, complete catalog coverage, or successful Android/Desktop integration.
- Tests have been authored but not run by this reviewer. Main and canonical hashed equal at review time. The adjacent UI/router are actively being developed by another executor and were outside this review.
