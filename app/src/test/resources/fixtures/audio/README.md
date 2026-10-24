# Studio waveform corpus

Original generated PCM WAV fixture, not music or third-party audio. Two seconds, 48 kHz, 16-bit signed little-endian stereo; channel R is the negative of L. At 0-0.5 and 1.5-2 seconds the signal is silent; at 0.5-1 seconds it is a 440 Hz sine at amplitude 0.2, at 1-1.5 seconds amplitude 0.8.

Each sample is round(32767 * amplitude * sin(2*pi*440*frame/48000)). The opposite-phase channels detect erroneous channel averaging; quarter-second windows and independent envelope bounds detect time shifts, silent output and normalization errors. Unit tests read the complete container; device tests use the production MediaExtractor/MediaCodec path. Fixture presence is not evidence of a device pass.
