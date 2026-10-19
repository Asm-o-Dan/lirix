# Multilingual translations v3: verification
Date: 2026-10-19. Direct implementation and self-review by Codex; no agents or independent review claimed.

## Delivered
LyricsTranslate automatic discovery is connected on Android and Windows. Shared reader offers ru/en/es/pt/zh selection independent of UI locale. Selection persists per device. DAO observations, saved-first lookups, transactional automatic save, lookup caches and visible states distinguish track and target language. Existing schema v12 and Russian documents remain compatible. Manual links dispatch to either supported source and cannot save under a mismatched target.

Russian remains Amalgama-first with LyricsTranslate fallback; other targets use LyricsTranslate. Provider submits the source's actual POST form with observed language options, validates exact normalized metadata and loads unique matches for page identity verification. Multiple authors remain candidates. A bounded title-only fallback handles joined browser channel names; every candidate still requires exact normalized artist and title/version. It is not a full-site crawl; first-page/first-eight candidate limits can miss some available variants.

Direct requests with the explicit Lirix identifier returned200. Earlier Python default requests had returned403; a permanent root cause is not claimed. Native JVM live checks now pass without browser cookies, CAPTCHA solving, proxy or impersonation.403/429 remain explicit failures with60s provider backoff. Source availability can change. Unicode URL slugs and observed extensionless/.html-N aliases are supported with strict host/path validation. Unpaired original/translation texts, source, author and warnings are preserved without invented karaoke timing.

## Final validation
Command: :desktop:test :shared:desktopTest :app:testDebugUnitTest :app:assembleDebug :desktop:createDistributable --offline --no-daemon --no-configuration-cache --max-workers=2.
Environment enabled live translation checks and actual Compose translation previews. BUILD SUCCESSFUL in4m36s after final source edits.
- Desktop122 discovered,120 passed,0 failures/errors,2 skipped: optional full-workspace preview and user-supplied Amalgama HTML probe.
- Shared173 passed,0 failures/errors/skips.
- Android198 passed,0 failures/errors/skips.
- Total491 passed. Relevant real Room tests protect language separation and existing manual selections. Form POST/redirect, metadata/version checks, forbidden redirects,403 backoff, preference validation/persistence, old JSON and Unicode/modern URL cases pass.
- Native provider searched Indila / Derniere danse from metadata and loaded verified ru/en/es/pt/zh translations. Multiple source authors required candidate selection in the probe. No song lyrics or real HTML fixtures committed/logged. Browser-style Hellraiser Anniversary/Official Animated title plus OzzyOsbourneVEVO found verified Spanish metadata through fallback.
- Actual detached Compose panel tests passed at360x740 and900x720, including initial automatic save without URL input, clicking Espanol, separate saved language rows and disappearance of the Russian document from the visible panel. Narrow/wide Spanish previews visually inspected. Outputs: desktop/build/translation-previews.
- git diff --check passed.

## Package verification
Source and packaged jar SHA256 match:
- shared-desktop.jar:91146763ad2f10244bb622e09c746a2972bef5d114d43853238f571e18f5a729
- desktop.jar:64b82c55229118a714c8c90a555a103f0c855da90fdaa006e566f0ad88fa1cc8
PC: desktop/build/compose/binaries/main/app/Lirix/Lirix.exe.
APK: app/build/outputs/apk/debug/app-debug.apk,27957866 bytes.

## Acceptance limits
No installed process was stopped/restarted and no APK installed. Physical device/browser acceptance remains pending. No GitHub release published. Translation archive/merge inclusion and bilingual karaoke remain separate work. App UI localization is separate from song translation target selection.
