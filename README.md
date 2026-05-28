# Balfouriana

**Regulatory reporting engine for UK/EU funds.**

Small and mid-sized AIFMs outsource the compliance work they remain legally responsible for. Balfouriana replaces that outsourced process: ingest raw custodian data, apply deterministic validation and rules, generate regulator-ready filings, and maintain a complete immutable audit trail.

## Current status

Phases **1–6** are complete. The pipeline runs end-to-end for **MiFID II** (generation, XSD validation, submission, ACK/NACK). **AIFMD** and **EMIR** rule paths work; filing templates for those regimes are stubs until prioritized. **Phase 7** (E2E hardening) is next — see [master-execution-plan.md](master-execution-plan.md).

```
ingest → validate → rules → filing → submit → ACK → exception ops → audit export
```

## Highlights

- Single Spring Boot app, event-sourced domain model, correctness-first delivery
- Versioned validation packs, rule packs, and MiFID filing templates
- Local outbox submission by default; optional SFTP; ACK drop-zone ingestion
- Exception queue with operator resolve/resubmit and regulator-ready audit export
- Demo console (`frontend/`) over REST APIs; sample data in `balfouriana-demo-data/`
- Marketing site ([`website/`](website/)) → [balfouriana.com](https://balfouriana.com) (Next.js + Vercel; separate from demo console)

## Quick start

```bash
./gradlew bootRun
./gradlew test
```

Operational detail (env vars, drop zones, SFTP): [HELP.md](HELP.md). Architecture and principles: [design.md](design.md).

## Pipeline (what ships today)

| Step | Scope |
|------|--------|
| **Ingest** | `POST /ingest`, filesystem drop zone under `./data/ingest/incoming/` |
| **Validate & enrich** | Versioned Step 2 packs; stub LEI/instrument/venue enrichment |
| **Rules & calculations** | MiFID, AIFMD Annex IV, EMIR packs; `FilingReadyRecordEvent` + trace metadata |
| **Filing (MiFID)** | MiFIR XML renderer, XSD gate, local outbox or SFTP submit |
| **ACK loop** | CSV/XML drop zone; linked NACK and orphan handling |
| **Exception ops** | Open queue, dismiss/acknowledge, Step 4 resubmit (`forceResubmit`) |
| **Audit export** | Full timeline JSON download per correlation or filter |

Deferred: real AIFMD/EMIR filing file quality (5e/5f); live GLEIF/FIRDS; external rule-pack feeds.

## Demo APIs

| Endpoint | Purpose |
|----------|---------|
| `GET /demo/scenarios` | List demo fixture files |
| `GET /demo/scenarios/{scenario}/{fileName}/ingest` | Ingest a scenario file |
| `GET /demo/runs` | Recent pipeline runs |
| `GET /demo/runs/{correlationId}` | Full event chain for a run |
| `GET /demo/runs/{correlationId}/summary` | Run status summary |
| `GET /demo/exceptions` | Open exception queue (filters: severity, source step, correlation) |
| `POST /demo/exceptions/{id}/resolve` | Dismiss or acknowledge an exception |
| `POST /demo/exceptions/{id}/resubmit` | Retry Step 4 after NACK/submission failure |
| `GET /demo/audit/export` | Regulator-ready audit bundle (attachment download) |

## Step 3 rules (AIFMD example)

- Pack: `step3-aifmd-annex-iv-calcs`
- Regime precedence via `RegulatoryRegimeSelector`: EMIR → MIFID_II → AIFMD_II
- Calculations: leverage, LOF cap, delegation, LMT, loan concentration, risk retention
- Trace metadata on `FilingReadyRecordEvent`, `RuleDecisionEvent`, `CalculationAppliedEvent`

## Frontend demo console

1. Backend: `./gradlew bootRun`
2. Frontend:
   ```bash
   cd frontend
   npm install
   cp .env.example .env
   npm run dev
   ```
3. Set `ENGINE_API_BASE_URL=http://localhost:8080` in `frontend/.env`

CORS for `http://localhost:3000` and `http://localhost:5173` is enabled in backend config.

## Docs & plans

- [HELP.md](HELP.md) — env vars and operational runbooks
- [master-execution-plan.md](master-execution-plan.md) — phase roadmap
- [website/](website/) — marketing landing page ([website/master-execution-plan.md](website/master-execution-plan.md))
- [phase-5a-plan.md](phase-5a-plan.md) … [phase-6c-plan.md](phase-6c-plan.md) — completed phase notes
