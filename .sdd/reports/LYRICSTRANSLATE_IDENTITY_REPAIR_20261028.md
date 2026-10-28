# LyricsTranslate RU→EN identity repair — 2026-10-28

`LyricsTranslateProvider` retains existing exact metadata search but an automatic `Found` now additionally requires source/request edition and collaborator qualifiers to agree, on both search-row identity and loaded document. Remaster, anniversary, feat and ampersand collaboration mismatch return manual Candidates rather than auto-saving a different version. Cyrillic ↔ Latin artist or parenthesized Latin transliteration of Cyrillic title is a bounded possibility only, never automatic proof; source row becomes a Candidate requiring confirmation, not a fabricated transliteration match. Same previously supported normalized exact identity remains eligible for Found.

Live metadata-only ordinary HTTPS 2026-10-28: GET `/en/songs` returned 200 with `lyricstranslatesearch-searchsong-form` POST `/en/songs`, English option `328`; POST song `Я свободен`, artist `Кипелов`, English 328 returned 200 and multiple rows `Я свободен (Ya svoboden)`, artist `Kipelov`, including URLs `/en/kipelov-ya-svoboden-english` and `...-0`, authors MSK and OreWell. GET first source returned 200 `text/html; charset=utf-8`, heading `Я свободен (Ya svoboden) - English translation`, artist `Kipelov`, original title `Я свободен`, translated title `I Am Free Now`; original/translation selectors present. No actual lyrics were printed or saved. Fixtures copy only minimal structural selectors/metadata, with synthetic lyrics.

Four new production-provider tests exercise Russian→English form POST, actual observed Cyrillic/Latin aliases as candidates, load and English/artist/title/translator, exact Found, remaster/anniversary/featured collaborator mismatch as Candidates, wrong artist and wrong target. They have not been run by the executor; source licensing and real network acceptance beyond metadata remain separate.

The earlier 15 Rustoeng JUnit methods now use `runBlocking<Unit>`, preserving assertions and yielding JVM void. This corrects the initializationError from JUnit runner before tests ran. No Gradle/APK run; changed only the paths below, matching main/canonical.

| File | SHA256 main = canonical |
|---|---|
| `shared/src/jvmShared/kotlin/com/lirix/app/translations/LyricsTranslateProvider.kt` | `0c3c39fc36862d166a1ad730213c09a904cc5b294677b0fe13231d0080119024` |
| `desktop/src/test/kotlin/com/lirix/app/desktop/LyricsTranslateRussianEnglishTest.kt` | `0c517697f943b7bb09c3f5d6432aff358bddf7ea94204f13af3b6dc75aa8ee00` |
| `desktop/src/test/kotlin/com/lirix/app/desktop/RustoengTranslationProviderTest.kt` | `88668d945e7a54f4dd341d1ad7537fa9682cf069f86ea466b35400322fc5870b` |
| `desktop/src/test/resources/translations/lyricstranslate-ru-en/form.html` | `d13b7ae619dc1e241455db3bb6814c69302a79a16a3f1ba1458fc97fe1693db9` |
| `desktop/src/test/resources/translations/lyricstranslate-ru-en/results.html` | `943e988e2e05aa7606435ae7f5994fe7dfe774036452378452985e4b138f67c3` |
| `desktop/src/test/resources/translations/lyricstranslate-ru-en/song.html` | `8d9d0f3d73910263272fe2cccfd81b84d6bebf370a63f22269ed02fc4fa9544b` |
| `desktop/src/test/resources/translations/lyricstranslate-ru-en/README.md` | `f9b33d48d5c27e9e1e328da4eed84a5e2373049e6a0ecd94e4e2f08e036f8fa5` |

## Artist qualifier and wrong-target correction freeze

Review found that artistKey discards parenthesized qualifiers. Automatic identity now compares the complete normalized artist suffix from the first opening parenthesis, including nested or malformed suffixes; different `(UK)`/`(US)` or unverified aliases remain Candidates. Complete standalone channel labels `(Official)`, `(Official Channel)`, `(Topic)` and `(VEVO)` are the only ignored parenthesized suffixes. Provider regression covers changed search artist, changed document after matching search, nested parentheses, matching qualifiers, and benign official label. This deliberately favors manual confirmation when uncertainty remains.

`wrongArtistAndWrongTargetCannotBecomeFound` previously supplied `French`, which is unsupported by the parser and therefore correctly throws `IllegalStateException` before comparing to expected `en`. It now supplies supported `Spanish` and still expects `IllegalArgumentException` from the explicit target mismatch guard. No rejection assertion was removed or weakened.

No Gradle/APK was run by executor; parent owns actual verification and independent review. Updated hashes:

| File | SHA256 main = canonical |
|---|---|
| `shared/src/jvmShared/kotlin/com/lirix/app/translations/LyricsTranslateProvider.kt` | `d76d46fd0fa024da68ea5c7a020bd7dd9bd4fc86d444b9ee99b94c928115f4ad` |
| `desktop/src/test/kotlin/com/lirix/app/desktop/LyricsTranslateRussianEnglishTest.kt` | `2985974ad879f501eb2e517ad39115e947abdcca474cea31459fa306052fae1e` |
| `desktop/src/test/kotlin/com/lirix/app/desktop/RustoengTranslationProviderTest.kt` | `88668d945e7a54f4dd341d1ad7537fa9682cf069f86ea466b35400322fc5870b` |
