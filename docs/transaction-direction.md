# Transaction direction and safe reparsing

Direction is resolved independently of the bank-specific amount parser. The live
`IsolatedExtractorRunner`, universal extractor, learned templates and backfill all
use `TransactionDirectionResolver`. The live runner keeps existing static parsers
for their amount/balance accuracy, then tries universal extraction for other formats.
Both notification title and body are evidence, including compact `CashBack0,70RUP`.

## Conservative outcomes

- Completed incoming money, top-ups, cashback and refunds are CREDIT; refunds retain
  their separate `isRefund` flag. Purchases/withdrawals/outgoing transfers are DEBIT.
- Generic or own-account transfers remain TRANSFER; they are not inferred income.
- Missing or contradictory evidence is UNKNOWN/SUGGESTED, never default DEBIT.
  Unknown direction has a neutral, unsigned UI and is excluded from totals.
- OTP, promotional offers, pending rewards and negated operations are suppressed.
  Failed attempts retain DECLINED status and are excluded from income/expense totals.
- Template AUTO resolves each notification. Explicit template choices cannot override
  contradictory direction or turn authentication/promotional text into a transaction.

The resolver currently recognizes Russian, English, Romanian and selected
transliterated expressions. It is extensible, not a promise to understand every
language or bank format. Existing trusted bank/SMS source gates remain enforced;
an arbitrary app is not trusted just because its notification contains an amount.

## Existing data

Room v6 adds persisted operation and user-confirmation status. The v5→v6 migration
preserves existing rows and indexes. Old rows keep their historical defaults because
the old schema did not record those facts; their original status cannot be recovered
from the migration alone.

Explicit template backfill can correct direction/amount/refund status on previously
automatic rows. USER_CONFIRMED/USER_EDITED records are protected from automatic
reparsing; a subsequent explicit user edit can still replace a prior user edit.
Suppressed notifications are skipped; this change does not destructively remove old
false positives or automatically reprocess the entire history.

## Regression checks

Use the normal Gradle test targets (`:core:model:test`, `:core:text:test`,
`:extract:finance:test`, `:extract:universal:test`, `:induction:test`,
`:pipeline:runtime:test`, and Android library unit-test variants for replay/storage/UI).

The new direction fixtures are synthetic/redacted. They cover title-only incoming
money, compact cashback, top-up/purchase amount and balance preservation, refunds,
conflicts, generic/own-account transfers, negation, promotions, OTP, unknowns and
malformed long amounts. Template/engine/backfill tests verify the same semantics.

A dependency-free SQLite regression suite runs the checked-in migration and DAO SQL:

```sh
python3 -m unittest discover -s core/storage/src/test/python -v
```

It complements Room/Android tests; it does not replace an Android build or Room's
migration validation.
