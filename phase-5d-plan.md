# Balforiana - Phase 5d Detailed Plan

## Purpose

Phase 5d completes the Step 4 acknowledgement loop: poll ARM/TR ACK files from a local drop zone, link reliably to `FilingSubmittedEvent` via `submissionId`, and make unresolved ACKs auditable and queryable—without Phase 6 exception/resubmit workflows.

## Design Guardrails (Must Hold)

- Source of truth: `design.md`
- One Spring Boot app; extend `service/filing` + config + repository only
- No new event types — reuse `FilingAcknowledgementReceivedEvent`
- Mirror ingest drop-zone semantics (stability window, processing/failed dirs, no embedded SFTP)

## Phase 5d Scope

- In scope: ACK drop-zone config and poller, typed submission lookup, sentinel correlation for orphans, idempotent ACK ingestion, unresolved query helper, tests, docs.
- Out of scope: Phase 6 exception queue, automated resubmit, remote SFTP ACK pull, official ARM XML schema validation, REST upload endpoint.

## Deliverables (completed)

### 5d.0 ACK drop-zone config

- Extended `FilingStep4Properties.acknowledgement.dropZone` + `application.yaml` env vars
- Directory layout: `incoming/`, `processing/`, `failed/` under `./data/filing/ack`

### 5d.1 ACK drop-zone poller

- `AcknowledgementDropZonePoller` — scheduled poller mirroring `DropZonePoller`
- Routes `.csv` → `ingestCsv`, `.xml` → `ingestXml`

### 5d.2 Robust submission lookup

- `EventStoreRepository.findFilingSubmittedBySubmissionId(submissionId)`
- `AcknowledgementIngestionService` uses typed lookup instead of payload string scan

### 5d.3 Unresolved ACK handling

- `UNLINKED_ACK_CORRELATION_ID` sentinel for orphan ACKs
- `findUnresolvedAcknowledgements(limit)` repository helper
- `ValidationAuditQueryService.unresolvedAcknowledgements()`
- Idempotent guard: skip duplicate `submissionId` + status (or `externalReference` + status when unlinked)

### 5d.4 Tests

- Extended `AcknowledgementIngestionServiceTest` — linkage, orphans, dedupe, XML
- `EventStoreRepositoryAckIntegrationTest` — submission lookup + unresolved query
- `AcknowledgementDropZonePollerIntegrationTest` — end-to-end drop zone

## Acceptance Criteria

- ACK CSV/XML files in `ack/incoming/` are polled and persisted
- Linked ACKs share the original filing `correlationId`
- Unlinked ACKs use sentinel correlation and are queryable
- Duplicate ACK ingestion is suppressed
- `./gradlew test` passes

## Follow-on

- **Phase 6** — exception queue, NACK → correction/resubmit workflows, demo ACK samples
- **Phase 7** — JSON/submissionId index on event store, official ARM ACK XML schema
