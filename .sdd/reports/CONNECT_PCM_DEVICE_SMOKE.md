# Connect device smoke — 2026-10-16

Android device: 2440cbe2 (Poco), com.lirix.app 1.4.0 / code17, updated with adb install -r; data preserved.
Windows: fresh desktop:createDistributable build, started from desktop/build/compose/binaries/main/app/Lirix/Lirix.exe. Old installed copy closed. Runtime TCP and UDP port4433 bound by this executable.

Initial observation: TCP connected from phone; user reports green indicator but no audio. Android dumpsys media_projection=null; no capture service. Identified a fabricated delayed HandoffAck/RemoteStateUpdate in the main device-selection handler. Fixed under CR-CON-PCM-03 to invoke actual audio capture consent and service startup.

After APK rebuild/install, user explicitly confirmed: «Разрешил, звук появился». Android dumpsys media_projection then reports active com.lirix.app TYPE_SCREEN_CAPTURE. This is a PASS for first audible playback Android → Windows, not merely a socket-level test.

Installed debug APK SHA256: EEBF91E3E83E72411900A2E0199E437A3223C5DF0B489C442B6916DDAFBC4626.

Sustained 60-second playback and stop/start retest: PASS, user explicitly confirmed «Да, всё работает». Reconnect/security/handoff remain separate work. Release publication has not occurred.

Automated evidence: shared desktopTest167 tests/0failures; app testDebugUnitTest197 tests/0failures; PCM desktop receiver/socket9 tests/0failures. Full desktop suite initially31 tests/1 failure in reverse-cast metadata test due IO task ordering. Test now uses injected coroutine scope for deterministic unit assertions; rerun pending. This does not prove Windows→Android casting or solve its existing codec/capture gaps.
