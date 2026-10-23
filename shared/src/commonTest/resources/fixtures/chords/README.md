# Chord transposition corpus

Gold-style intro row `Intro: Am (Em) F (C) }x2` reproduces the reported structure only. Remaining section rows and negative prose are independently authored diagnostic inputs. No song lyric text is included. Expected +2 output is manually authored independently from the production parser; wrappers, headers, whitespace and ordinary prose must remain unchanged.

These are resource fixtures loaded by the JVM shared test suite. Extended/slash/Unicode/invalid/OOV tokens also have explicit golden and pitch invariants in ChordTransposerRepairTest. A fixture pass does not assert unknown text is a chord or guarantee enharmonic spelling after arbitrary non-octave round trips.
