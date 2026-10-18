# DESK-UX-101 — buildDesktopLibraryViewState

Status: READY FOR COORDINATOR SELECTION; not implemented.
Spec: .sdd/specs/media-ui/desktop_library_theme_corrections.md v1.
Owner: desktop coder; one function card.
Function: `buildDesktopLibraryViewState`.
File: `New desktop/library/DesktopLibraryViewState.kt` (relative to desktop/src/main/kotlin/com/lirix/app unless New path).
Dependencies: None.

## Deterministic scope

Implement enums/data view model and only pure function specified. Stable filter+query+artist intersection, validated karaoke, selection and emptystate. No DB/IO/UI mutation.

## DoR

Spec accepted; dependencies integrated; current working copy checked; no unrelated changes. Supporting imports/data type declarations required by named function allowed; no second behavioural function in this card.

## DoD

Pure matrix from spec; no UI/Room tests needed.

Run relevant existing checks once after integrated batch; record results and exactmanuallimitations. No broad layoutredesign, schema migration, added audio features, release publication or visualPASS without actual evidence. Commit coordination belongs to root; do not commit another worker's edits.
