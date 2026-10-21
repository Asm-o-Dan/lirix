# Japanese / Chinese romanization — implementation proposal

Requested: think through Japanese/Chinese transliteration alongside the final karaoke studio. This is research and architecture, not a claim that conversion is already installed.

## Display and data contract
- A separate optional reading layer: original / original + reading / reading. Keep original lyrics, translations and timing independent.
- Reuse the original line occurrence ID and line timestamp. Never infer word or syllable timing from spelling length. Repeated choruses must stay distinct.
- Cache key: exact original text hash + language + romanization scheme/options + engine/dictionary version. Manual line corrections override regeneration; original remains recoverable.
- Whole-line conversion first. Do not claim character alignment until normalization and tokenizer offsets are mapped back to the original UTF-16 positions.
- Language is explicit when ambiguous: Han characters alone cannot distinguish Chinese from Japanese. Mandarin pinyin is not Cantonese Jyutping.

## Japanese candidate
1. Analyze the whole phrase into word readings using Kuromoji (Apache-2.0 code; check each dictionary license independently). It exposes kanji readings; start by benchmarking IPADIC on Android.
2. Convert kana readings to Hepburn romaji using wanakana-kt core (MIT); it alone does not solve kanji readings.
3. Preserve punctuation and non-Japanese runs; allow manual correction for names, irregular/song-specific readings and pronunciation differing from written lyrics.
4. Test long vowels, small kana, sokuon, syllabic n, particles, mixed English/Japanese, halfwidth kana, unknown proper names. NFKC only in the analysis copy; never overwrite the stored original.

## Chinese candidate
- Evaluate houbb/pinyin (Apache-2.0), which documents phrase segmentation, polyphonic readings, simplified/traditional input, custom dictionaries and output with tone marks / tone numbers / no tones.
- Default proposal: Mandarin pinyin with tone marks, optional no-tone display. Custom phrase/manual corrections are needed; dictionary results do not establish the pronunciation actually sung.
- TinyPinyin is a lightweight alternative for a no-tone mode, but its own README explicitly excludes tones/dialects, so it does not meet the full proposed display contract by itself.
- Test polyphones in context, neutral tone, ü, punctuation/numbers/emoji, mixed Latin runs and traditional characters. Do not silently label Cantonese songs as Mandarin.

## Integration sequence and feasibility gates
1. Offline JVM/Android spike on a small permitted test corpus. Measure conversion quality, cold start, cancellation, memory and APK/dictionary size before selecting/installing dependencies.
2. Shared provider/result interface with per-language implementations; lazy initialization and background conversion, cancelled on text/language changes. No network upload of lyrics by default.
3. Per-line cached reading and manual corrections, then optional karaoke display. A reading layer never changes the song's LRC or studio markers.
4. If Japanese dictionaries are too large, consider an optional versioned language pack; test integrity, offline availability and errors before promising it.

## Primary sources inspected
- https://github.com/atilika/kuromoji — readings for kanji; dictionary-specific APIs/licenses.
- https://github.com/esnaultdev/wanakana-kt — kana/romaji conversion, Kotlin core artifact and MIT license.
- https://github.com/houbb/pinyin — documented tone styles, segmentation, polyphones and user dictionaries.
- https://github.com/promeG/TinyPinyin — Java/Android, explicitly no tones/dialects.

No dependency added and no transliteration UI shipped yet. Existing Android UX physical acceptance remains pending a phone; karaoke studio implementation is now active, with model/draft/UI/loop/waveform and runtime checks still tracked as work.
