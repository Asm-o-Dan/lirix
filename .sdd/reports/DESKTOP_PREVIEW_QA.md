# Optional Compose preview harness: independent review — 2026-10-18

Reviewed `DesktopComposePreviewTest.kt` read-only during the executor's proof compilation. QA did not run Gradle or change harness/product code. **No safety blocker found for the opt-in artifact run.** Compilation and rendered-image quality remain separate gates.

- The environment assumption executes before output directory creation and fixture construction. Ordinary tests skip the harness without opening its database or rendering a scene.
- Fixture Room, lyrics cache and scope are temporary. Injected automatic lookup fails rather than contacting providers. No active custom source rules are inserted. The sample media ingress is inert.
- The harness invokes actual `DesktopWorkspace` and production desktop fonts, palette tokens and the same Material color-scheme mapping as Main. It does not call Main, create a native application window, register hotkeys, run tray/audio ingress, open file dialogs or invoke persistence callbacks.
- Playback is paused at a fixed 3000ms baseline. Supplied metadata/LRC and scene dimensions are explicit. It still uses current timestamps for sample history and the current user's existing sync-offset value; artifacts are not byte-deterministic fixtures.
- Each image and encoded data resource is closed, each scene is closed in finally, then fixture jobs are cancelled/joined before Room close and temporary-directory cleanup.
- Sidebar events target detached scene logical coordinates and operate the actual page state; no Windows UI input is generated. Each theme/size scenario begins with a fresh scene. Page labels must be confirmed from the resulting image, not inferred from filename.

Important limits: three immediate frames do not prove asynchronous Wrapped calculation has completed; a loading frame may be captured. Root must inspect actual PNG content/destination. Production Deck reads the existing offset Preferences; no setter is exercised by these sidebar clicks or inert theme callbacks. Deck also obtains the Connect singleton: its current constructor creates an in-memory trusted store and unstarted coroutine scope, while sockets/audio start only in `start`, which this harness never calls. This is not full application integration isolation and should be rechecked if that constructor gains startup side effects.

This harness can support review of real Compose layout. It does not establish native drag/DPI/focus, hotkeys, file chooser, audio, installation, theme restart persistence or full interactive acceptance. PNG creation and a passing test are not visual quality assertions.
