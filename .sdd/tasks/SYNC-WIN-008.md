# SYNC-WIN-008: prepareBridgeResources

Status: VERIFIED_IMPLEMENTATION; physical acceptance pending

Specification: ../specs/media-core/desktop_playback_sync_fix.md

Owner: sync_fix_coder. Independent review: sync_regression_qa.

Function: `prepareBridgeResources` Gradle resource staging task.

Acceptance: tracked native executable is staged into Compose's windows resource directory; packaging depends on staging, missing source fails, packaged executable matches published executable SHA256 and runtime resource lookup path.

DoD: independent build-script/consumer review and successful fresh distributable with hash evidence. No manual patch to the generated application image.

