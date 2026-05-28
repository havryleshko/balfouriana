# Balforiana - Phase 6b Detailed Plan

## Purpose

Phase 6b closes the exception loop from 6a: operators can dismiss/acknowledge queue items or resubmit Step 4 filings after NACK/submission failure, with immutable resolution audit events.

## Design Guardrails (Must Hold)

- Source of truth: `design.md`
- Immutable resolution events only — never mutate source exception events
- No field-level correction in-app; upstream fixes require re-ingest via `/ingest`
- MiFID-first resubmit path; same orchestrator when `FilingReadyRecordEvent` exists
- Out of scope: audit export (6c), frontend inbox UI, auto re-ingest

## Phase 6b Scope

- In scope: resolution/resubmit domain events, repository helpers, queue projection updates, `ExceptionResolutionService`, REST actions, `forceResubmit` on orchestrator, tests, docs.
- Out of scope: regulator-ready audit export (6c), demo console buttons, auto-`SUPERSEDED` on new ingest.

## Deliverables (completed)

### 6b.0 Resolution domain events

- `ExceptionResolutionContracts.kt` — `ExceptionResolvedEvent`, `ExceptionResubmitRequestedEvent`, `ExceptionResolutionType`
- `ExceptionQueueStatus.RESOLVED` + optional resolution fields on `ExceptionQueueItem`
- Registered in `DomainEvent.kt` + serialization tests

### 6b.1 Repository resolution helpers

- `findLatestResolutionByQueueItemId`, `findResolvedQueueItemIds`, `hasResolutionForQueueItem` on `EventStoreRepository` / `JdbcEventStoreRepository`

### 6b.2 Queue projection updates

- `ExceptionQueueMapper.enrichWithResolution`
- `ExceptionQueueQueryService.openExceptions(..., includeResolved = false)` excludes resolved by default
- `findQueueItemById` for resolution lookups

### 6b.3 Resolution service + orchestrator

- `ExceptionResolutionService.resolve(...)` — dismiss/acknowledge with guards by source step
- `ExceptionResolutionService.resubmitFiling(...)` — uses latest `FilingReadyRecordEvent`, emits resubmit request + resolved on success
- `SubmissionOrchestratorService.process(..., forceResubmit = false)` bypasses 5c submit idempotency when true

### 6b.4 REST actions

- `POST /demo/exceptions/{queueItemId}/resolve` — body: `resolutionType`, `note`, `resolvedBy`
- `POST /demo/exceptions/{queueItemId}/resubmit` — body: `note`, `resolvedBy`
- `GET /demo/exceptions?includeResolved=false` — default hides resolved items

### 6b.5 Tests

- `ExceptionResolutionServiceTest`, extended mapper/query/orchestrator tests, `ExceptionResolutionIntegrationTest`
- `./gradlew test` green

## Acceptance Criteria

- Resolved exceptions disappear from default open queue
- Operator can dismiss review/ops/blocking validation-rule items
- Operator can resubmit Step 4 after NACK/submission failure when `FilingReadyRecordEvent` exists
- `forceResubmit` bypasses duplicate-submit guard for explicit operator retry
- Resolution and resubmit attempts are immutable audit events

## Follow-on

- **6c** — regulator-ready audit export including resolution events
- Optional demo console buttons for resolve/resubmit
- Auto-`SUPERSEDED` when new ingest clears same reason code
