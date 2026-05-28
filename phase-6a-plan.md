# Balforiana - Phase 6a Detailed Plan

## Purpose

Phase 6a adds a unified read-model exception queue over existing Step 2–4 failure events, with query service and REST list API — no resolution/resubmit (6b) or audit export (6c).

## Design Guardrails (Must Hold)

- Source of truth: `design.md`
- Read projection only — no new write events
- All queue items have `status = OPEN`
- MiFID-first scope; AIFMD demo scenarios used for review/blocking paths

## Phase 6a Scope

- In scope: `ExceptionQueueItem` contracts, repository multi-type scan, mapper, query service, `GET /demo/exceptions`, tests, docs.
- Out of scope: resolution lifecycle, resubmit workflows, audit export, frontend inbox UI.

## Deliverables (completed)

### 6a.0 Queue contracts and config

- `ExceptionQueueContracts.kt` — `ExceptionQueueItem`, severity/source step enums
- `OpsProperties` + `balfouriana.ops.exception-queue` in `application.yaml`

### 6a.1 Repository helper

- `EventStoreRepository.findByEventTypesSince(eventTypes, sinceInclusive, limit)`

### 6a.2 Query service

- `ExceptionQueueMapper` — maps validation/rule/filing/ACK events to queue items
- `ExceptionQueueQueryService.openExceptions(...)` with filters and dedupe
- `ValidationAuditQueryService.openExceptions(...)` delegate

### 6a.3 REST API

- `GET /demo/exceptions` with optional `limit`, `severity`, `sourceStep`, `correlationId`

### 6a.4 Tests

- `ExceptionQueueMapperTest`, `ExceptionQueueQueryServiceTest`, `ExceptionQueueIntegrationTest`

## Acceptance Criteria

- Single API returns normalized open exceptions from Steps 2–4 + orphan/NACK ACKs
- Demo scenario reason codes (review/blocking) visible in queue
- Filters work; `./gradlew test` passes

## Follow-on

- **6b** — `RESOLVED` status, correction/resubmit workflows
- **6c** — regulator-ready audit export
- Optional demo console inbox panel
