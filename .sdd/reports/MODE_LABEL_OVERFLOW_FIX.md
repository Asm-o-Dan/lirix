# Mode label overflow fix

## Runtime evidence

The unchanged Android instrumentation acceptance criterion in `PlayerSurfaceUiTest.largeFontModeLabelsFitAndRemainSelectableOnNarrowPhone` initially failed for `Lyrics` at 320 dp and font scale 2.

A first correction using sizing Text subcompositions failed the next real run: the unmerged semantics tree contained two Karaoke labels. Unplaced sizing nodes remain discoverable there, and `clearAndSetSemantics` on a wrapper does not remove descendants from that tree. Those subcompositions have been replaced entirely by TextMeasurer.

A subsequent Russian run still failed for `Текст`. Diagnostic assertions established the exact cause: actual Text size 182 x 81 px, paragraph width 185 px, paragraph height 81 px, constraints maxWidth 185 px / maxHeight 161 px; `didOverflowWidth=true`, `didOverflowHeight=false`, line edges 1..184 px. Thus the measured height was sufficient; the shrink-wrapped centred Text box was three pixels narrower than its paragraph. The earlier intrinsic-height hypothesis was not proof of this failure's cause.

## Final correction

The selector measures each label with `rememberTextMeasurer` at its exact available width without a height cap. Measured and displayed labels share the same merged Material style, selected font weight, alignment and composition-local density/font resolver. The largest text height plus existing vertical padding determines all four equal-width target heights, with a 48 dp minimum. A normal Layout measures the four actual targets once at that common size. TextMeasurer creates no UI/semantics nodes.

The actual displayed Text additionally uses `fillMaxWidth`: its box now receives the same fixed paragraph width rather than shrink-wrapping only the centred glyph bounds. This addresses the observed width mismatch directly, preserving font size, original text and wrapping. No truncation, manually inserted characters, weaker overflow assertion or replacement label was introduced. Selected/tab semantics and RTL-relative placement remain intact.

The existing assertion now reports layout sizes, paragraph dimensions, width/height overflow flags, constraints, styles and line edges without changing its acceptance condition. Both RU and EN must still pass the original no-overflow/selectability checks in real runs.

## Validation boundary

Both component copies and both test copies are identical across the canonical isolated checkout and main tree. This executor did not start Gradle. The coordinator must rebuild and rerun the original test in RU and EN; source correction alone is not runtime proof. Extremely height-constrained parents still impose their own maximum, as required by the Compose layout contract.

## Coordinator runtime validation
Final build and release lint PASS. Four Android tests PASS on API35 emulator under both real RU and EN app languages, including the unchanged large-font overflow/selection criterion. Detailed evidence: PLAYER_STUDIO_EMULATOR_20261024.md. This proves the selector correction, not the full goal.
