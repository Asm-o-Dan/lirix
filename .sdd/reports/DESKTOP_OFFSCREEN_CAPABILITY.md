# Actual Compose offscreen preview capability

2026-10-18. Read-only investigation; no implementation, Gradle invocation, dependency download, application launch or Windows automation.

## Supported by the installed binaries

The cached `org.jetbrains.compose.ui:ui-desktop:1.7.3` JAR contains `androidx.compose.ui.ImageComposeScene` and top-level `androidx.compose.ui.renderComposeScene`. `javap` confirms the scene constructor accepts pixel width/height, Density, LayoutDirection, CoroutineContext and composable content; `setContent`, `render(nanoTime): org.jetbrains.skia.Image`, `hasInvalidations`, `close`, scene-local pointer/key input and roots are available. Kotlin metadata/compile will determine any required experimental opt-in.

Disassembly confirms `ImageComposeScene` creates `Surface.makeRasterN32Premul(width,height)` and `CanvasLayersComposeScene`, renders into that raster canvas and returns a snapshot. This is actual Compose layout/text/drawing with CPU Skia, not native screen capture and not a substitute HTML layout. Cached `skiko-awt:0.8.18` and `skiko-awt-runtime-windows-x64:0.8.18` provide the required runtime. Skia Image exposes `encodeToData(EncodedImageFormat, quality)`; Data exposes `bytes`. No additional dependency is required by the identified API.

## Smallest useful harness

A desktop test-source artifact generator can construct ImageComposeScene, set the real theme/font composition locals and MaterialTheme exactly as Main does, render the actual DesktopWorkspace or DesktopDeckScreen, encode PNG, then close image/data/scene and all fixture jobs/database. Use existing desktop test runtime classpath and compiler, not a new screenshot library. First prove one idle player PNG before building a matrix.

For Workspace, create a real temporary Room database and DesktopLibraryRepository with its own supervised scope; seed deterministic tracks/sessions/achievements before rendering and await the relevant StateFlow values. Supply DesktopLyricsService with an isolated cache directory and a no-network automatic lookup. Pass a simple fixture MediaIngressProvider with fixed state and no-op transports, explicit fixed DesktopLyricsState, fake art or no art, no-op settings callbacks and immutable HudSettings. Never call Main/application, actual ingress construction, global hotkey registration, tray, Discord startup or Connect.start.

Workspace's `page` is currently private remembered state defaulting to Player, while library/wrapped/settings composables are private. Therefore rendering Workspace directly starts Player only. Options: (1) send scene-local pointer events to the actual sidebar then render again, with known size/density, or (2) approve a narrow optional initial-page parameter as a real navigation entry-point seam. Option 1 needs no product source changes and exercises existing navigation but is more brittle. Do not duplicate panel markup into previews or change private visibility across the UI just to obtain screenshots.

Use six AppThemeType values at 1280×820 and minimum native size 980×680, density 1; add one density 1.5 case with pixel dimensions scaled to preserve logical dimensions. Capture empty player, timed lyrics, plain text/no-timing state, library empty/populated/long metadata and Wrapped empty/populated. A long-text fixture is necessary to expose clipping; random sample data is unsuitable.

Use explicit paused playback/time and stable seeded timestamps for reproducibility. Render a bounded number of frames at explicit nanosecond times to settle composition, fonts and initial effects; await repository flow readiness before scene creation. Do not loop indefinitely until hasInvalidations is false: animated cursors, progress indicators and player effects may keep invalidating. Preserve visual failures as artifacts for inspection instead of treating existence/dimensions of a PNG as acceptance.

## Important limits

- DesktopFeatureDialogs currently wrap content in native `DialogWindow`. Calling these wrappers can create real windows; do not include them in an alleged offscreen harness. To preview editor/Tap-to-Sync/poster/help body, approve extracting each real body into a composable used by both the wrapper and the renderer. Likewise the HUD Window wrapper needs a shared content composable if HUD content is to be rendered offscreen. Do not fake wrappers or claim current dialogs are already supported.
- DesktopDeckScreen reads DesktopSyncOffsetStore's Windows Preferences singleton, so its screenshot depends on existing offset unless the harness selects plain/idle content or an approved override removes that read. It does not write preferences unless interaction changes the offset. Merely redirecting user.home is not sufficient on Windows. DesktopWorkspace callbacks must remain no-op for settings; repository data/cache are temporary.
- Deck obtains Connect singleton trustedStore but does not start the server. Keep Connect dialog closed. A fresh renderer JVM has no Main startup integrations.
- Offscreen scene input is synthetic input to a detached composition only. It does not verify native focus, hotkey registration, accessibility, DPI transitions between monitors, window movement/resize, click-through, always-on-top, tray, file choosers, audio or installation.
- CPU raster rendering can differ from native GPU output at antialiasing/subpixel boundaries. Fonts must use production DesktopFonts and actual typography locals. Capture and visually inspect images before deciding design quality.

Estimated scope: one test-source harness and fixtures for Player/Workspace static previews is bounded; all native dialogs/HUD would require separately approved content extractions. A successful PNG run would unblock static visual review of captured states, not native acceptance or release readiness. No API has been execution-verified in this investigation.

## Approved optional harness — awaiting first execution

`desktop/src/test/kotlin/com/lirix/app/desktop/DesktopComposePreviewTest.kt` now contains the artifact generator. Normal tests skip before creating fixtures unless `LIRIX_RENDER_PREVIEWS=1`. It performs no product-source changes and uses only existing dependencies. The UX coder owns the coordinated Gradle invocation; the preview executor did not run a competing build.

First-proof PowerShell invocation, after the normal JAVA_HOME/GRADLE_USER_HOME setup:

```powershell
$env:LIRIX_RENDER_PREVIEWS = '1'
$env:LIRIX_PREVIEW_DIR = 'C:\Users\DaniilTuT\Documents\antigravity\quick-nobel\desktop\build\compose-previews'
& 'C:\Users\DaniilTuT\Documents\antigravity\quick-nobel\gradlew.bat' -p 'C:\Users\DaniilTuT\Documents\antigravity\quick-nobel' :desktop:test --tests com.lirix.app.desktop.DesktopComposePreviewTest --offline --no-daemon --no-configuration-cache --max-workers=2
```

The first invocation leaves `LIRIX_PREVIEW_MATRIX` unset and should create only `obsidian_pulse-1280x820-idle.png` plus README. Inspect the actual image before enabling `LIRIX_PREVIEW_MATRIX=1` and repeating the targeted task with `--rerun-tasks` if Gradle considers it up-to-date. The optional full mode adds populated fixture screenshots at six themes and two widths. Its scene-local pointer destinations require image inspection: filename alone is not proof that navigation succeeded. Remove opt-in environment variables from the invoking process after artifact generation so later ordinary tests remain skipped.

## Harness correction after visual inspection

The first 61-image matrix did not prove Wrapped or Settings: fixed-coordinate pointer sequences left those outputs on Library. The original three frames also advanced animation time by only about 50ms. Minimum-width karaoke clipping from those frames must be checked again after settling before it is classified as a stable layout defect.

The corrected test invokes the actual sidebar node's SemanticsActions.OnClick from the detached composition, and verifies a content-area heading before writing a destination PNG. Wrapped additionally requires its calculated "Любимые треки" content. Settings requires "Чёрный список". This validates the rendered destination and production callback, not mouse hit testing.

In Compose 1.7.3, ImageComposeScene.roots throws NotImplementedError. To inspect the real tree without dependencies/product changes, the optional harness uses a version-specific reflection adapter for its private scene → mainOwner and the public semantics owner getter. It fails if these internals change; it never substitutes a fake tree or duplicated page layout. Every PNG receives a sibling `.semantics.txt` with actual text/bounds for independent inspection.

Each capture now advances at least 90 actual scene frames (1.5 seconds virtual) and requires stable text/bounds across 12 frames; the hard cap is 180 frames. A 1ms scheduling opportunity per frame lets Default-dispatcher effects run. Failure to reach the required destination/readiness or stable layout fails the generator rather than silently naming a wrong image. This is bounded rendering, not a native readiness claim.

Focused proof: set `LIRIX_RENDER_PREVIEWS=1`, `LIRIX_PREVIEW_FOCUSED=1`, leave MATRIX unset, use a fresh LIRIX_PREVIEW_DIR and the same targeted command above. It writes idle plus Paper & Ink at 980×680: settled karaoke, Library, actual Wrapped and actual Settings. Coordinator owns Gradle and image inspection before expanding the matrix. No run of the corrected harness is claimed in this document yet.

## Corrected focused execution — confirmed

The coordinator executed the corrected focused generator successfully (46-second Gradle invocation; JUnit reports 1 test, 0 failures/errors, generator time 10.663 seconds). Artifacts and actual semantics dumps are in `desktop/build/compose-preview-settled`.

The coordinator opened the images and confirmed Wrapped now has its real "Итоги прослушиваний" heading and calculated statistics, while Settings has its real heading, sliders and settings content. Therefore the corrected navigation/readiness mechanism has execution evidence for these focused cases. PNG names were not used as the acceptance criterion.

The coordinator also inspected settled Paper & Ink karaoke at 980×680: the long active fixture paragraph remains clipped at the bottom after the bounded animation settling. This is now evidence of a persistent minimum-width layout problem, not merely the original early-frame ambiguity. Product correction and regression verification are owned by the coordinator; the preview executor made no product changes and ran no Gradle.

The full corrected six-theme matrix has not been rerun at this checkpoint. Native-window acceptance remains open. Later typography/palette/active-anchor product changes require fresh artifacts before these images can describe the resulting version.
