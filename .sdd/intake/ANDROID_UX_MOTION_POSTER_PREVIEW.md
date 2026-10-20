# Android UX: motion and poster preview

User accepted smoothness of the first prototype, requested a slightly slower tempo, screen transitions and preview before poster export. Direct implementation/minimal process authorized.

- Wrapped reveal: 1500 ms instead of 900 ms; keep real values and stable text layout.
- Root tabs: 360 ms small directional slide and fade; saveable tab state retained. Teach/settings overlays: restrained fade/slide. Compose system duration scale remains authoritative (including zero).
- Common track/Wrapped poster dialog: square/stories preview, explicit Send and Save actions, original bitmap reused for export. Save uses Android document picker, no storage permission. Selected format must match preview before export is enabled; failures retain dialog and allow retry.
- No renderer/backend/database/desktop/release changes. Preserve concurrent unrelated edits; stage only this task's NowPlaying hunk.

Verification: Android compile/unit tests; PNG export roundtrip pixels/dimensions and inaccessible destination. Device acceptance pending: slower tempo, transitions/rapid tabs, system animations off, both formats/themes, cancel/save/share and long text. ADB currently has no connected phone.

Validation: isolated build (`--init-script build/ux-baseline/isolated-build.gradle --no-configuration-cache :app:assembleDebug :app:testDebugUnitTest`) PASS. 225 Android tests, including exact PNG pixels/dimensions and injected output failure, pass. Shared build directory was busy with another build, so validation used build/ux-verification; no other process was stopped.
Test artifact: build/ux-motion-preview/lirix-ux-preview.apk, SHA256 764c24b5e1d68fcebbcd8a654eaa4c370ec1243be03437cdbf9fd21e92e8f125. Local debug certificate matches the phone's previous local build. Public release unchanged. Device UI acceptance remains pending because ADB has no device; do not mark the overall UX goal complete.
