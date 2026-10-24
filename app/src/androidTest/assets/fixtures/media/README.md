# MP4 codec regression corpus

Two original 64 x 64 RGB PNG swatches authored for this project (red #ff0000, blue #0000ff), no third-party material. These checked-in raster media fixtures are decoded via BitmapFactory from instrumentation assets and rendered for the first/second half of a real AVC export. They are not mock encoder bytes or a simulated MP4 container.

The expected file contains independent 1080 x 1920 / 24 fps / 96-frame / four-second acceptance parameters. MediaExtractor verifies every muxed sample and PTS; a real MediaCodec decoder verifies every decoded PTS and center U/V chroma, catching planar/interleaved channel swaps. Measured compressed-payload bitrate must be positive; a reported container bitrate, when present, must also be positive. Flat swatches compress below the requested target, so target bitrate is not asserted equal to measured rate.

This corpus diagnoses transport/codec integrity. It is not a visual acceptance test for arbitrary album art, font coverage, or every vendor codec. No device pass is claimed by fixture presence.
