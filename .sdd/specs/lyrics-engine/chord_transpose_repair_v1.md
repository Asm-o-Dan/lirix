# Chord transpose repair v1 — APPROVED 2026-10-23

## Defect and scope

Gold-style `Intro: Am (Em) F (C) }x2` is rejected because only two of six whitespace tokens match the existing chord regex and the line falls below 60%. Parenthesized tokens also fail anchored parsing. Repair shared ChordTransposer only, preserving caller/API compatibility. No new dependencies or UI changes. Root owns execution, review, commit and ADB.

## Exact function contracts

- `transposeNote(rawNote, semitones)`: accept existing A–G/H root aliases, ASCII #/b and Unicode ♯/♭; invalid full strings unchanged, never consume only a prefix. Reduce shift modulo 12 BEFORE addition to avoid Int overflow. Zero and octave multiples preserve input spelling exactly. Existing flat-vs-sharp preference otherwise retained. Pitch round-trip, not arbitrary enharmonic spelling, is guaranteed.
- `parseChordToken(raw): ParsedChord?` (new private helper): retain leading/trailing whitespace and balanced outer ()/[]/{} wrappers; parse the entire core root + known musical suffix + optional /bass. Return source offsets/root/suffix/bass, never perform free-text substring substitution. Accept composed suffixes such as m7b5, maj9, 7sus4, add11, dim7, aug, 6/9, m(maj7), alterations (b5/#5/b9/#9/#11/b13) and parenthesized alteration lists. Slash bass is the last /note, distinct from numeric 6/9. No arbitrary alphabetic suffix. Public direct chord conversion remains case tolerant; bare text chord lines require uppercase root initials to avoid interpreting English articles as chords.
- `transposeChord(chord,semitones)`: consume parsed whole token; transpose root AND optional bass; preserve suffix/wrappers/whitespace byte-for-byte. Unknown/malformed token unchanged. Octave/zero unchanged.
- `isChordLine(line)`: strip only recognized initial structural label (Intro/Verse/Chorus/Bridge/Outro/Instrumental/Solo/Pre-Chorus plus Russian equivalents, optional number, colon or bracketed section label). Remaining content must contain >=1 chord and consist EXCLUSIVELY of recognized chord tokens, bar/repeat separators and repeat counts (x2, 2x, }x2 etc.). No ratio heuristic. Any prose/unknown token vetoes whole-line bare transposition. Structural labels, bars, repeat counts themselves never change.
- `transposeChordLine(line,semitones)`: replace parsed chord core spans only; every delimiter and gap preserved exactly. Wrapped and slash-bass forms participate equally. Header/repeat tokens excluded.
- `transposeBracketedLine(line,semitones)`: in otherwise-prose lines transpose only explicit square-bracket complete chords; unknown bracket content remains verbatim. Preserve spaces within brackets. Parentheses in prose are not chord markup.
- `transposeText(text,semitones)`: classify each line once; transpose accepted chord rows, otherwise only explicit square bracket chords. Preserve original CRLF/LF/CR, all nonchord characters and spacing. Zero/octave multiples return exact input.

## Tests and anti-green gates

Existing tests remain. New checked-in fixture under shared/src/commonTest/resources/fixtures/chords/ contains Gold structural chord rows with no song lyric text, plus independently authored jazz/slash/alteration forms and negative prose. JVM resource loading belongs to commonTest JVM-compatible existing project conventions; no production dictionary/mocks. Golden input and independently authored expected +2 output compare exactly. Unknown/malformed forms and mixed prose remain exact. This is not fabricated copyrighted lyric content.

Adversarial matrix covers every fixture chord root AND bass, +/-12 identity, +n then -n pitch identity, Unicode accidentals, sharp/flat enharmonics, H alias, INT_MIN/MAX safe modular arithmetic, repeated sections, tabs/CRLF, nesting/wrappers, unsupported suffixes, English prose containing A/I/am/be and bracket annotations. Invariants assert untouched nonchord spans and all recognized chord spans transformed (no selective omissions); full parsed root/bass pitch matches independent 12-note oracle rather than reproducing implementation regex.

DoD: coordinator records RED on old implementation for Gold row, wrapped/extended chords and prose-preservation; then GREEN actual shared suite and caller compilation. No test-pass claim before execution. Scope delivery includes exact source/test/resource paths and clean HEAD patch, no unrelated changes.
