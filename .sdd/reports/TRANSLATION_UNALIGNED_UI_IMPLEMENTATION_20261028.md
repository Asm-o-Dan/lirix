# Translation-only / unaligned UI — 2026-10-28

UI changes prepared, not compiled, tested on device or visually inspected by executor. `aligned=false` in Together renders separate original and translation sections, without falsely pairing lines: uses independently stored track original first, then original fields from the document; if neither exists displays one localized unavailable message. Translation mode displays translated content only. Wide mode uses two columns, narrow stacks sections, and compact mode displays the non-alignment explanation as well. Existing aligned mode remains on the prior branch. In dirty main tree, originalLineContent hook is preserved and applied to actual independent original lines, not the missing-original placeholder; no hook exists in HEAD, so canonical does not invent one.

Existing UiMessageCatalog keys for Original, Translation, Together, missing-original and non-alignment provide RU/EN; no shared string catalogue edited. HEAD had mode labels and message hardcoded in Russian; canonical changes just these relevant labels and explanation to existing LirixStrings keys.

## Preservation and source identity

The initial canonical TranslationPanel was unexpectedly byte-identical to dirty main and already carried unrelated localization and originalLineContent changes relative to `git show HEAD:shared/src/commonMain/kotlin/com/lirix/app/translations/TranslationPanel.kt` at HEAD 178623e. Never stage that earlier whole blob. Canonical file was recreated from actual `git show HEAD` plus just the scoped unaligned branch, three mode labels, untranslated explanation and missing-original fallback. It has no originalLineContent API and does not transplant foreign main reading-layer or other localization. Main file retains its prior dirty localization/reading-layer and receives analogous narrow unaligned changes.

## Tests

`UnalignedTranslationPanelTest` uses a real desktop `ImageComposeScene`, actual TranslationPanel, in-memory DAO and synthetic SongTranslation, without new dependencies, against RU and EN, narrow/wide views. Four JUnit cases cover independent track-original precedence, translated text, original/translation/together modes, document-original fallback, one unavailable message, compact disclaimer, and unchanged aligned rendering. Their assertions inspect semantic UI output, not merely model data. No screenshots or Gradle runs by executor, so neither test compilation nor success is claimed; test runtime environment must support ComposeScene. The dirty main-only `originalLineContent` integration is preserved by source inspection but cannot be verified by this canonical test without incorporating foreign UI changes.

## Frozen SHA256

| File | Main | Canonical |
|---|---|---|
| `shared/src/commonMain/kotlin/com/lirix/app/translations/TranslationPanel.kt` | `d8373dd96ac43ea8df191fd6fadb8a77a3cf878b3e5a03f7a87590174a1c6d5a` | `3bd34ecf17e447336ce0234186eeb8d8f719e097f21d3e7d98387b1d15a2c09f` |
| `desktop/src/test/kotlin/com/lirix/app/desktop/UnalignedTranslationPanelTest.kt` | `4ab3b0445fdb1da0d01589355177d5fe308c4252fc65a8cb0eff026d8b10804b` | `4ab3b0445fdb1da0d01589355177d5fe308c4252fc65a8cb0eff026d8b10804b` |

Do not infer provider-specific auto-discovery, HTTP availability, authorisation for content reuse, persistent archive behavior or Android/desktop live acceptance from these UI changes.


After first targeted compilation failure, removed invalid explicit import `androidx.compose.ui.semantics.getOrElse` from test in both trees. SemanticsConfiguration.getOrElse is already used without that explicit import in existing preview test. No other test or product modifications; repeat compilation/runtime pending.
