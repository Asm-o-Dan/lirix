# Automatic Russian translations v2 — 2026-10-19
Status: approved correction from user: automation is the intended feature; manual URL import is only a fallback.

## Behavior
Resolve on active-track metadata changes, independently of selecting the Text tab. Desktop application owns the coordinator; Android NowPlaying owns it across mode changes. Reader also resolves an offline/library track when opened. Saved variants win without HTTP, even on forced retry. Exact unique artist+title match is fetched, verified against the actual page headings, then saved automatically. No manual URL, checkbox or save button on this path. Together is the reader's initial mode when automation is supplied.

## Discovery
Fetch source letter catalog, parse only #songs_nav links, normalize artist labels, follow the real matching artist URL, then parse song links from that catalog. When artist spelling maps to a different bucket, use source .bands_list popular-artist links as a bounded fallback; never guess song/artist slugs or use a paid search API. Letter/site directory addresses follow observed site navigation. Song heading identity must match normalized requested metadata before automatic binding.

Normalize Unicode accents/punctuation, The/artist-article order, VEVO/Topic/Official channel suffixes, featuring credits and known presentation tags. Normalize first English number words for artist aliases (Thirty/30). Preserve Live/Remix/Acoustic qualifiers for exact matching. Base title without qualifiers is only used to propose candidates. Duplicate exact song URLs, multiple matching artists or differing headings/versions require user choice/preview. No invented timing or karaoke changes.

## Reliability
Existing HTTP/charset/redirect/body limits apply to catalogs too. Compact catalog-link lists cache for 24h, maximum96 documents; explicit retry bypasses them. Coordinator serializes competing view requests, checks Room first, negative/candidate result TTL15min, error TTL1min, bounded128 status entries. Track cancellation never acknowledges/saves late data. Network work precedes a Room transaction which saves only if no translation was selected in the meantime. No schema-version change beyond existing v12.

## Acceptance
Synthetic catalog fixtures test real link following (including relative links), sidebar exclusion, heading identity, duplicate/variant choice, channel and article/number normalization, changed format and redirect safety. Coordinator tests verify saved-first, automatic persistence, repeat/coalescing, TTL/retry, late cancellation and real Room concurrent manual selection protection. Detached reader test must find and persist a synthetic translation without any click or URL entry and capture searching/ready at narrow/wide sizes. Rebuild both targets and perform live packaged-provider discovery from metadata, not a known song URL. Physical device acceptance stays separate.

Verified source exception policy: the actual Hellraiser page spells its artist Ozzy Ozbourne. After (and only after) a unique exact catalog artist+title match, accept one substituted character in an artist heading of at least10 normalized characters. No insertions/deletions, no short-name or catalog fuzzy matching; title/version matching remains exact. Other mismatches still require choice. Regression covers the boundary and prevents tolerance leaking into catalog discovery.
