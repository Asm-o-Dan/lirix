# Repository cleanup — 2026-10-20

Historical sprint plans, deployment notes, architecture and UI design documents were moved to the documentation tree. They remain available rather than being discarded.

Old root APKs, device UI/logcat dumps, unused cloudflared executable and personal phone/network/AI helper scripts were removed from tracking. Local copies are preserved and explicit ignore rules prevent accidental re-addition. The product's required native bridge and useful reusable development tools are retained. Existing commits and published release assets are unchanged; this is not a history rewrite or secret rotation.

The published v1.4.0 tag's release workflow explicitly sets `prerelease: true`; GitHub API confirms that release is published and marked prerelease, with Android and Windows assets. The workflow now derives the status from the tag: `v1.4.1` is stable, while `v1.4.1-rc.1` is prerelease. Changing the workflow does not retroactively change the existing v1.4.0 release flag.

Validation: inspect rename detection and affected references; ensure no APKs, root log/UI dumps or unneeded cloudflared executable remain tracked; verify ignored local copies remain present; validate workflow expressions and clean staged diff. No application source code is changed by this cleanup.
