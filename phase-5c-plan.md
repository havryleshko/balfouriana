# Balforiana - Phase 5c Detailed Plan

## Purpose

Phase 5c hardens Step 4 submission: honest local-outbox default (mirroring ingest), optional real SFTP upload when configured, correct `FilingGeneratedEvent` linkage on submission events, and idempotent resubmit protection—without ACK workflow changes (5d).

## Design Guardrails (Must Hold)

- Source of truth: `design.md`
- One Spring Boot app; extend `service/filing` + config only
- Immutable events; fix linkage without mutating prior records
- Default transport is honest `LOCAL_OUTBOX`; no fake `sftp://` URIs unless mode is SFTP

## Phase 5c Scope

- In scope: submission mode config, `LocalOutboxFilingSubmissionClient` / `SftpFilingSubmissionClient` split, audit linkage fix, idempotent submit guard, tests, docs.
- Out of scope: ACK/NACK poller and linkage hardening (5d), decoupling Step 3→Step 4 trigger, AIFMD/EMIR submission paths.

## Deliverables (completed)

### 5c.0 Submission mode config and client split

- `FilingSubmissionMode` (`LOCAL_OUTBOX` | `SFTP`)
- Extended `FilingStep4Properties` + `application.yaml` env vars
- `LocalOutboxFilingSubmissionClient` — writes payload to `local-outbox-dir`; returns `file://` URI
- `SftpFilingSubmissionClient` — real JSch upload when `mode=SFTP` and host configured
- `FilingSubmissionClientConfiguration` wires one active client by mode
- `com.github.mwiede:jsch` dependency

### 5c.1 Fix submission event linkage

- `SubmissionOrchestratorService` captures `FilingGeneratedEvent` and reuses `generatedEvent.metadata.eventId` for `FilingSubmissionRequestedEvent`, `FilingSubmittedEvent`, and `FilingSubmissionFailedEvent`

### 5c.2 Idempotent submit guard

- `EventStoreRepository.hasSuccessfulSubmission(correlationId, outputChecksumSha256, filingTemplateVersion)`
- Orchestrator skips submit block when a prior successful submission exists for the same fingerprint (generation audit still emitted)

### 5c.3 Tests

- `SubmissionOrchestratorServiceTest` — linkage, idempotency, schema fail-closed
- `LocalOutboxFilingSubmissionClientTest` — file written to configured dir
- `SftpFilingSubmissionClientTest` — mock `SftpOperations` transport
- Integration tests pass with default `LOCAL_OUTBOX`

## Acceptance Criteria

- Submission events reference the real `FilingGeneratedEvent.eventId`
- Default mode writes honestly to local outbox; optional SFTP mode uploads when configured
- Duplicate submit for same correlation + checksum + template version is suppressed
- `./gradlew test` passes

## Follow-on

- **5d** — ACK drop-zone poller, robust submission lookup, unresolved-ack handling
- **Phase 6** — exception queue and resubmission workflows
