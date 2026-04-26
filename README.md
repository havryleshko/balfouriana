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
