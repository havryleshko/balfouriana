# Balfouriana Demo Dataset — Halkin Family Office

A single fund. A single trading day. Three filing scenarios.
Designed to drive every path through the `balfouriana` rule engine in a 90-second hackathon demo.

## The fund

**Halkin Family Office Limited** — UK AIFM, £200m AUM, open-ended structure.
Custodian: State Street. Investment manager: in-house, with delegation to Goldman Sachs Asset Management for fixed income.

| Field | Value |
| --- | --- |
| Fund LEI | `2138001HALKIN00FAM12` |
| Decision-maker LEI | `2138001HALKIN00DM345` |
| Counterparty LEI (GS Intl) | `W22LROWP2IHZNBB6K528` |
| NAV | £200,000,000 |
| Fund structure | OPEN_ENDED |
| Reporting regime | MiFID II + AIFMD II + (EMIR if derivative) |

## The trade (2026-04-30)

Halkin's portfolio manager places a single buy order:

- **Instrument**: Vodafone Group Plc (ISIN `GB00BH4HKS39`)
- **Quantity**: 200,000 shares
- **Price**: £0.7250
- **Notional**: £145,000
- **Venue**: London Stock Exchange (`XLON`)
- **Counterparty**: Goldman Sachs International
- **Settlement**: T+2 → 2026-05-04

By end-of-day, the custodian SFTPs four canonical records to the engine:

1. **TRADE** — the execution itself (MiFID II transaction reporting feed)
2. **POSITION** — EOD holdings snapshot with AIFMD II fund-level metrics
3. **CASH_MOVEMENT** — the £145k settlement leg
4. **CORPORATE_ACTION** — a separate BP dividend received the same day

## Three scenarios, same trade

| Folder | Path through engine | What changes |
| --- | --- | --- |
| `01-clean-auto-file/` | **PASS → auto-files** | Everything within caps. Engine emits `FilingReadyRecordEvent` directly. |
| `02-needs-review/` | **NEEDS_REVIEW → escalates** | Fund recently restructured, `aifmd_fund_structure` is `HYBRID` and the LMT-used flag is blank. Engine cannot decide; routes to a human. |
| `03-blocked-breach/` | **BLOCKING ERROR → halts** | Largest borrower in the loan book is 30% of total exposure, breaching the 20% AIFMD concentration cap. |

All three scenarios use the same trade, same fund, same counterparty — only the AIFMD fund-level metrics on the POSITION record differ. That's deliberate: the demo proves the engine routes by **what the data says**, not by which file it came from.

## How to ingest

Once `balfouriana` is running (`./gradlew bootRun`):

```bash
# REST upload
curl -F "file=@01-clean-auto-file/halkin-eod-2026-04-30.csv" \
     http://localhost:8080/ingest

# Or drop into the watch folder
cp 01-clean-auto-file/halkin-eod-2026-04-30.csv \
   ./data/ingest/incoming/
```

Watch the event store — you should see `FileReceivedEvent` →
`CanonicalRecordMappedEvent` × 4 → `CanonicalRecordValidatedEvent` × 4 →
`RuleDecisionEvent` × N → `CalculationAppliedEvent` × N →
`FilingReadyRecordEvent` (clean) **or** review/blocking metadata on the artifact.

## What each scenario should look like in the inbox

- **01-clean** → 4 records, all green, ready to file. `aifmd_review_required = false`, `aifmd_blocking_error_count = 0`.
- **02-review** → 3 green + 1 amber on the POSITION record. `aifmd_needs_review_count >= 1`. Reasons: `AIFMD_FUND_STRUCTURE_UNKNOWN`, `AIFMD_LMT_INDICATOR_MISSING`.
- **03-block** → 3 green + 1 red on the POSITION record. `aifmd_blocking_error_count >= 1`. Reason: `AIFMD_LOAN_CONCENTRATION_BREACH`.

These are the exact metadata fields already emitted by the rule engine — your agent layer reads them and routes the record to the right column of the inbox.
