# SYNC-WIN-005: GsmtcWatcher.ExecuteCommandAsync seek branch

Status: VERIFIED_IMPLEMENTATION; physical acceptance pending

Specification: ../specs/media-core/desktop_playback_sync_fix.md

Owner: sync_fix_coder. QA: sync_regression_qa. Review/build: playback_sync_architect.

Function: `GsmtcWatcher.ExecuteCommandAsync seek branch`

Acceptance: Honor native success and current-session identity before reanchor and immediate emission.

DoR: source diagnosis approved by coordinator; existing protocol retained.

DoD: behavior covered by applicable regression cases in the specification, independent review, native build, bundled binary SHA256 verification. Physical browser acceptance remains a separate gate.

