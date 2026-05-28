# Balforiana - Phase 6c Detailed Plan

## Purpose

Phase 6c completes Step 5 auditability: a regulator-ready JSON export of the full immutable event timeline (ingest through resolution/resubmit), filterable by correlation, submission, artifact, regime, and date window.

## Design Guardrails (Must Hold)

- Source of truth: `design.md`
- Read-only projection — no new write events, no payload mutation
- Full immutable payloads included for regulator traceability
- `GET /demo/runs/{correlationId}` unchanged for demo debugging; export is compliance-oriented
- Out of scope: PDF/CSV, signed legal-hold packages, frontend download UI, FCA/ESMA traceability matrix (Phase 7)

## Phase 6c Scope

- In scope: audit contracts, event-type registry, repository lookups, `AuditExportService`, REST download endpoint, tests, docs.
- Out of scope: streaming/zip exports, payload field DB indexes, demo console button.

## Deliverables (completed)

### 6c.0 Audit export contracts

- `AuditExportContracts.kt` — `AuditExportBundle`, `AuditTimelineEntry`, `AuditExportSummary`, filters, links
- `Step4Summary` moved to domain (shared with demo run summary)
- `AuditEventTypes.kt` — ingest through OPS registry + `DECISION_CHAIN` subset

### 6c.1 Repository lookup helpers

- `findCorrelationIdBySubmissionId`, `findCorrelationIdsByArtifactId` on `EventStoreRepository` / `JdbcEventStoreRepository`

### 6c.2 Audit export service

- `AuditExportService.export(...)` — correlation/submission/artifact/window resolution, timeline assembly, summary
- `decisionChainByCorrelationId` delegates to shared filter (includes 6b ops events, excludes ingest)
- `OpsProperties.auditExport` config in `application.yaml`

### 6c.3 REST export endpoint

- `GET /demo/audit/export` with optional `correlationId`, `submissionId`, `artifactId`, `regime`, `start`, `end`, `limit`
- `Content-Disposition: attachment` for one-click JSON download
- Delegates via `ValidationAuditQueryService.exportAudit(...)`

### 6c.4 Tests

- `AuditExportServiceTest`, `AuditExportIntegrationTest`, `AuditExportGoldenTest`
- `./gradlew test` green (145 tests)

## Acceptance Criteria

- Single export returns ordered full-pipeline timeline including 6b resolution/resubmit events
- Filters work: correlationId, submissionId, regime, date window
- Summary includes artifact, regimes, submissions, ACKs, exception/resolution counts
- Download headers enable one-click JSON handoff
- `./gradlew test` passes

## Follow-on

- Phase 7: traceability matrix, performance hardening, optional CSV/PDF export
- Demo console "Download audit" button
- DB index on payload fields if bulk export becomes slow
