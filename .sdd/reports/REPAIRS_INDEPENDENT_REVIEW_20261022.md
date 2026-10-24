# Independent repair review — 2026-10-22

## Scope and evidence

Read-only review of the frozen MP4 delivery in `build/repairs-baseline/mp4/head-output/`, exactly the 22 paths listed in `approved-product-files.json`, against `mp4_repair_v2.md`. No product/test edits, Gradle execution, device execution, installation, or delegation by this reviewer. Test results belong to the coordinator; none are inferred from source inspection. Romanization/localization are not yet reviewed in this report.

## Findings

### P2 — cancellation cleanup test can pass before the pipeline starts

`app/src/test/java/com/lirix/app/feature/share/animation/AnimatedPosterPipelineTest.kt:154–162`

The child `async` inherits the `runBlocking` dispatcher and is immediately cancelled before the parent suspends in `cancelAndJoin`. No barrier establishes that the encoder was created, a partial output was written, or rendering began. `isCancelled` and file absence can therefore pass without entering `generate` at all. Removing the production cancellation cleanup could leave this test green.

Required correction: fake encoder writes an identifiable partial file and signals an explicit started barrier from `encodeFrame`; parent awaits that barrier before cancellation. Assert cancellation propagation, exactly one abort, no finish/success progress, and deletion of that known partial file. The separate production-pump backpressure cancellation test is useful but does not prove pipeline cleanup.

### P2 — provider/share tests do not prove that the exported URI can be read

`app/src/test/java/com/lirix/app/feature/share/animation/ShareAnimatedPosterTest.kt:49–70`

The happy path checks only chooser/action/MIME/permission flags, not `EXTRA_STREAM` readability or exact bytes through the configured manifest provider. The outside-root negative test is meaningful and the video implementation correctly propagates provider lookup failure, but a future fabricated/unbound URI regression could still pass the happy-path assertions. `saveVideo` also has no injected destination-open/write failure assertion in this file.

Required correction: extract the actual `EXTRA_STREAM`, open it through the actual provider/content resolver, assert original bytes, and assert invalid authority/provider lookup returns false with no chooser. For save, assert null/unwritable/throwing output is surfaced, not reported as success. These are validation gaps, not a demonstrated new production sharing failure.

## Checks with no confirmed blocking defect

- The same production `Mp4CodecPump` is exercised by fake-adapter tests; no duplicated retry algorithm in tests. Frame indices advance after queue success only. Backpressure retries drain output, monotonic deadlines bound input/EOS waits, missing EOS and count mismatch fail.
- Constructor failure releases partial codec/muxer initialization. Pump failure preserves the original error and suppresses release failure. Normal finish requires muxer stop, release, positive output; abort does not deliberately finalize a partial muxer and the encoder/pipeline remove incomplete output.
- Flexible input uses the codec Image planes with row/pixel strides and closes the Image after filling; planar and semiplanar byte packing are distinct. This is static evidence only, not device compatibility proof.
- Pipeline cancellation callback reaches the pump. Final render includes t=1, lyric typing completes at .85, final progress is after successful finish. Complete text is measured before revealing prefixes, avoiding reflow. ICU grapheme boundaries are available at the verified Android minSdk 26.
- Real device instrumentation validates 1080x1920 AVC, 96 extracted/decoded frames, strictly increasing expected PTS, approximately four seconds, and red/blue chroma samples. Its presence is not an execution result. It does not certify bitrate/range/profile negotiation or every vendor encoder.

## Gate

No confirmed P1 product defect found in this frozen MP4 source review. Close the two P2 validation gaps before calling the repair fully verified under the current anti-green-mirage requirements. Real physical codec export/decode remains an explicit pending gate until the coordinator records an actual device run. No release readiness claim is made.
