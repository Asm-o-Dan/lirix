# Library removal implementation — 2026-10-27

Stage 1 implemented in main and canonical; frozen files below. Main/code and UI test report has no runtime claim. Horizontal gestures removed from track rows; ⋮ menu now explicitly offers current favorite toggle and library removal. `LibraryExclusions` stores persistent keys, serialized commits and StateFlow; UI filters tracks, artists and counts by same excluded set, allows per-key undo and persistent restore dialog, snackbar retains proper per-message timeout. Track data, file, favorites and history are not deleted; favorite uses atomic existing SQL invert. Dedicated RU/EN strings and both Android backup rules added. Existing manual archive does not include prefs. Tests use real SharedPreferences via Robolectric to cover restart of manager, two independent entries, concurrent additions/restorations, blank key. No tests/Gradle/device were run by executor. IntelliJ reports no errors for LibraryScreen and LibraryExclusions, XML parsed; compile and independent review remain required. User requested no APK; no device acceptance claim.

| File | Main SHA256 | Canonical SHA256 |
|---|---|---|
| `app/src/main/java/com/lirix/app/ui/LibraryScreen.kt` | `f5ac6619751fb16d6fae394a6755939da3f7d1a542adfd2b2f8c6d907fceabc6` | `f5ac6619751fb16d6fae394a6755939da3f7d1a542adfd2b2f8c6d907fceabc6` |
| `app/src/main/java/com/lirix/app/ui/components/TrackActionBottomSheet.kt` | `3172b64aeddc276b1e2771c657d1792f8c02ca6c28e23691e0be0e463707b2d5` | `3172b64aeddc276b1e2771c657d1792f8c02ca6c28e23691e0be0e463707b2d5` |
| `app/src/main/java/com/lirix/app/ui/library/LibraryExclusions.kt` | `0087b513118cfcad015beb06b3737148331bbc97b687a28aa2475656e43dc2d0` | `0087b513118cfcad015beb06b3737148331bbc97b687a28aa2475656e43dc2d0` |
| `app/src/main/res/values/library_removal.xml` | `42c37fe09f7f515f4bef174a0e15fde70b5e5279e07332abff3eaa8c5bea5a47` | `42c37fe09f7f515f4bef174a0e15fde70b5e5279e07332abff3eaa8c5bea5a47` |
| `app/src/main/res/values-ru/library_removal.xml` | `806a968cc1ec87690d44586be118759e989bec0043b57aad34fe8e853c2b2c7b` | `806a968cc1ec87690d44586be118759e989bec0043b57aad34fe8e853c2b2c7b` |
| `app/src/main/res/xml/backup_rules.xml` | `37f933bb675b5fb04865c8529d02b9c5a87400334bf2c49818fc7b2641a20793` | `37f933bb675b5fb04865c8529d02b9c5a87400334bf2c49818fc7b2641a20793` |
| `app/src/main/res/xml/data_extraction_rules.xml` | `d5e7c62d829c361007c6f7e3317b14efd31f01ef5b1227b46c665204914a5349` | `d5e7c62d829c361007c6f7e3317b14efd31f01ef5b1227b46c665204914a5349` |
| `app/src/test/java/com/lirix/app/ui/library/LibraryExclusionsTest.kt` | `1f156a936841f27c51abc0f0aad53ec8203e8fbf7958bf0b99798b207a0202a3` | `1f156a936841f27c51abc0f0aad53ec8203e8fbf7958bf0b99798b207a0202a3` |

## Updated freeze after independent review

`LibraryExclusions` derives writes from the last published state, compensates false/exceptional commits and never reads the potentially poisoned SharedPreferences map for subsequent updates. UI shows read failure instead of silently showing all tracks. Each removal returns a per-process generation token; Undo restores only if the token matches the current removal. Manual restoration invalidates old tokens. Cancellation is rethrown. Six Robolectric tests now include false commit plus failed compensation followed by unrelated success, exception/read failure, cancellation and interleaved stale undo. The wrapper emulates commit mutating in-memory prefs before reporting failure; this is not proof of actual disk-failure durability. Strong guarantees after simultaneous write and compensating write failure across process death depend on Android storage; this case must be reported and reviewed rather than overclaimed. Wording changed to history/files unchanged. No APK/Gradle run; IntelliJ errorsOnly reports no errors in screen, store, and tests.

| File | SHA256, main and canonical |
|---|---|
| `app/src/main/java/com/lirix/app/ui/LibraryScreen.kt` | `a11dea93562d9837d2c92baee8001ba124e78d262a1af51f2f717e6ffd9245f4` |
| `app/src/main/java/com/lirix/app/ui/components/TrackActionBottomSheet.kt` | `3172b64aeddc276b1e2771c657d1792f8c02ca6c28e23691e0be0e463707b2d5` |
| `app/src/main/java/com/lirix/app/ui/library/LibraryExclusions.kt` | `b60b5ec270df90c83d6c4db09474088d6e6a8a788f0d47381a0506fe83186638` |
| `app/src/main/res/values/library_removal.xml` | `2428b5e7691b5731f725a812b7cab71c255e020b608ce52e959b8572410fca30` |
| `app/src/main/res/values-ru/library_removal.xml` | `4e6ec1a1cc26719317533b06d803a1d44e832592f6f68f048c85540e70acfdce` |
| `app/src/main/res/xml/backup_rules.xml` | `37f933bb675b5fb04865c8529d02b9c5a87400334bf2c49818fc7b2641a20793` |
| `app/src/main/res/xml/data_extraction_rules.xml` | `d5e7c62d829c361007c6f7e3317b14efd31f01ef5b1227b46c665204914a5349` |
| `app/src/test/java/com/lirix/app/ui/library/LibraryExclusionsTest.kt` | `83650e7964ddb5cfc0b41e5f3f236ee340d74818901657aa36594938e951e9f6` |

## Cancellation repair freeze

A cancellation thrown after a preferences implementation mutates memory now triggers a best-effort compensation before the original CancellationException is rethrown. New-manager assertions immediately after cancellation and after failed write+failed compensation occur before any further successful operation. Fault injection simulates failed acknowledgements after in-memory changes; it does not simulate power loss or prove physical durability. A replacement such as Android AtomicFile could tighten the update protocol (write temp file and atomically finish/fail), but requires new storage/backup integration and tests; no system can guarantee successful storage under persistent I/O failure, and failed compensation must be disclosed. No Gradle/APK run by executor.

| File | SHA256 main and canonical |
|---|---|
| `app/src/main/java/com/lirix/app/ui/LibraryScreen.kt` | `a11dea93562d9837d2c92baee8001ba124e78d262a1af51f2f717e6ffd9245f4` |
| `app/src/main/java/com/lirix/app/ui/components/TrackActionBottomSheet.kt` | `3172b64aeddc276b1e2771c657d1792f8c02ca6c28e23691e0be0e463707b2d5` |
| `app/src/main/java/com/lirix/app/ui/library/LibraryExclusions.kt` | `3b2a3df21ce9c1e6756d6a67a120fe58d117b7df8add93d0a2c3be328c5c459f` |
| `app/src/main/res/values/library_removal.xml` | `2428b5e7691b5731f725a812b7cab71c255e020b608ce52e959b8572410fca30` |
| `app/src/main/res/values-ru/library_removal.xml` | `4e6ec1a1cc26719317533b06d803a1d44e832592f6f68f048c85540e70acfdce` |
| `app/src/main/res/xml/backup_rules.xml` | `37f933bb675b5fb04865c8529d02b9c5a87400334bf2c49818fc7b2641a20793` |
| `app/src/main/res/xml/data_extraction_rules.xml` | `d5e7c62d829c361007c6f7e3317b14efd31f01ef5b1227b46c665204914a5349` |
| `app/src/test/java/com/lirix/app/ui/library/LibraryExclusionsTest.kt` | `a98cdd592d8ddaf2e1876de257d07c6712895aae0f44badaf4c78bcd1fd593ed` |


## Root verification

2026-10-27 canonical :app:testDebugUnitTest --tests com.lirix.app.ui.library.LibraryExclusionsTest: BUILD SUCCESSFUL; XML 6 tests, 0 failures/errors/skips. Initial test run failed before test execution due to denied C:\.robolectric-download-lock; second run used cached offline SDKs and workspace worker directory. Kotlin compile PASS. Independent correction review PASS for simulated errors, not actual disk failure/process death. Native gesture, UI, backup, TalkBack and device visual checks remain unrun; no APK created.
