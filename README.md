# Balforiana

**Regulatory reporting engine for UK/EU funds.**

Small and mid-sized AIFMs currently outsource the compliance work they are legally responsible for. Balforiana replaces that entire outsourced process: it ingests raw custodian data and hands back regulator-accepted filings with a full audit trail as the complete outcome

## Highlights
- Single Spring Boot application with five focused modules
- Immutable, event-sourced domain model
- Correctness-first incremental implementation
- Currently in early development

## Quick Start
```bash
./gradlew bootRun
```

Run tests:

```bash
./gradlew test
```

## Ingestion (Phase 2)

Files: `POST /ingest` with multipart field `file`, or drop into `{ingest root}/incoming/` (see `balfouriana.ingestion.*` in `application.yaml`; override root with `BALFOURIANA_INGEST_ROOT`). Optional auth: set `BALFOURIANA_INGESTION_API_KEY` and send `X-Ingestion-Api-Key`. SFTP and operational detail: [HELP.md](HELP.md).

## Step 3 Rules Engine (Phase 4.3 Status)

Phase 4.3 is implemented on top of the Step 3 rule engine foundation.

- Dedicated versioned AIFMD II pack: `step3-aifmd-annex-iv-calcs`
- Deterministic multi-regime precedence: `EMIR` > `MIFID_II` > `AIFMD_II` > default
- AIFMD calculations/rules implemented:
  - Commitment leverage and gross leverage
  - LOF leverage cap checks (open-ended and closed-ended caps)
  - Delegation percentage and internal/delegated FTE split
  - LMT usage consistency
  - Loan concentration (20% borrower cap)
  - Risk retention (5% minimum)
- Runtime traceability on emitted events:
  - `RuleDecisionEvent.ruleResult` includes source authority/reference/published-at
  - `CalculationAppliedEvent.calculationMetadata` includes `regulatory_source_*`
  - `FilingReadyRecordEvent.traceMetadata` includes pack/version and applied calculation IDs
- Phase 4.5-compatible metadata now emitted on filing-ready outputs:
  - `aifmd_review_required`
  - `aifmd_blocking_error_count`
  - `aifmd_needs_review_count`
  - `aifmd_primary_metric`
  - `aifmd_primary_metric_value`

## Test Coverage (Current)

The current test suite covers:

- Rule pack selection and precedence behavior
- AIFMD calculation/rule pass, review, and blocking paths
- Step 3 audit chain events for AIFMD flows
- Domain event serialization for updated Step 3 contracts
- Deterministic output fingerprints and trace metadata checks

## Frontend Demo Console

The demo console lives in `frontend/` and consumes the engine APIs.

1. Start backend:
```bash
./gradlew bootRun
```
2. Start frontend:
```bash
cd frontend
npm install
cp .env.example .env
npm run dev
```

The frontend expects `ENGINE_API_BASE_URL=http://localhost:8080`.
Local CORS for `http://localhost:3000` and `http://localhost:5173` is enabled in backend config.

### Demo APIs for frontend

- `GET /demo/scenarios`
- `GET /demo/scenarios/{scenario}/{fileName}`
- `GET /demo/runs`
- `GET /demo/runs/{correlationId}`
- `GET /demo/runs/{correlationId}/summary`
