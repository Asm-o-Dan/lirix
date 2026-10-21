# KST-DURATION-001 — correct studio duration ownership

Status: DONE — independent re-review passed; fresh Android 260/shared 200 tests passed; physical studio acceptance remains separate.
Executor: karaoke_duration_correction
Contract: ../reports/KARAOKE_STUDIO_REVIEW_20261021.md — One bounded correction contract: studio duration ownership.

Scope: new KaraokeStudioDialog/KaraokeStudioDraftStore plus a small testable duration-policy helper and regression tests if needed. Preserve line content, original lyrics, marks, history and existing transactional Apply. No changes to unrelated playback/accounting work.

Acceptance: all seven regression scenarios in the independent review pass; existing studio/draft/PCM/backup suites remain green. Root owns build execution, independent reviewer owns final contract verification. No release or device data changes.
