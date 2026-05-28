# Balforiana - Master Execution Plan

## Overview

- This plan tracks delivery of a correctness-first regulatory reporting pipeline that produces regulator-ready filings with an immutable audit trail.
- Execution is strictly sequential by phase so each stage is production-viable before the next begins, with no architectural sprawl.
- Source of truth: `design.md`
- Architecture constraint: One Spring Boot app, minimal modules

## Current position (2026-05-24)

- **Pipeline in code:** ingest → validate → rules → filing (optional) → submit → ACK → event store; demo console and tests green.
- **Phase 5 (MiFID):** 5a–5d complete — generation, XSD gate, submission, ACK loop all production-credible for MiFID.
- **Phase 5 (deferred):** AIFMD/EMIR real filing templates (stub renderers only today); not blocking Phase 6.
- **Active work:** Phase 7 (E2E hardening); AIFMD/EMIR templates when prioritized.

## Phases

### Phase 1: Foundation and Platform Skeleton

- **Goal**: Get a clean, working Kotlin + Spring Boot project with proper structure and shared primitives.
- **Key Deliverables**: Project structure, domain event contracts, basic persistence, config, logging.
- **Dependencies**: None
- **Status**: Completed

### Phase 2: Step 1 - Data Hand-off and Canonical Ingestion

- **Goal**: Build reliable ingress that accepts fund/custodian data and normalizes it once into immutable internal events.
- **Key Deliverables**: SFTP/REST/drop-zone ingestion, parsers (CSV first), canonical event mapping, provenance capture, malformed-input rejection flow.
- **Dependencies**: Phase 1
- **Status**: Completed
- **Notes**: Ingest SFTP is external drop into `incoming/` (see `HELP.md`); REST `/ingest` and drop-zone poller implemented.

### Phase 3: Step 2 - Validation and Mapping

- **Goal**: Validate and enrich canonical events to produce a clean, regulator-safe stream with full decision traceability.
- **Key Deliverables**: Versioned validation packs (schema + business), enrichment adapters (LEI/instrument/venue), exception metadata, immutable validation logs.
- **Dependencies**: Phase 2
- **Status**: Completed (first cut)
- **Notes**: In-code validation packs and stub enrichment; external FCA/ESMA rule packs and live GLEIF/FIRDS deferred to Phase 7 / fin-regbase integration.

### Phase 4: Step 3 - Rule Application and Calculations

- **Goal**: Apply deterministic regulatory rules and calculations for MiFID II, AIFMD II, and EMIR.
- **Key Deliverables**: Versioned Kotlin rule sets, calculation components, filing-ready enriched events, end-to-end traceability for computed fields.
- **Dependencies**: Phase 3
- **Status**: Completed (first cut)
- **Notes**: MiFID, EMIR, and AIFMD rule packs; `FilingReadyRecordEvent`; confidence/escalation metadata (Phase 4.5). Regime precedence unified in Phase 5a via `RegulatoryRegimeSelector`.

### Phase 5: Step 4 - Filing Generation and Submission

- **Goal**: Generate correct regulator-ready outputs and submit them through supported channels.
- **Key Deliverables**: Versioned filing templates (MiFID/AIFMD XML, EMIR ISO 20022), submission orchestrators (SFTP first), ACK/NACK ingestion and linkage.
- **Dependencies**: Phase 4
- **Status**: Completed for MiFID (5a–5d); deferred sub-work for AIFMD/EMIR templates
- **Notes**:
  - **5a MiFID templates — Completed** ([`phase-5a-plan.md`](phase-5a-plan.md)): `RegulatoryRegimeSelector`, MiFID field passthrough, `MifidFilingTemplatePack`, golden-file renderer tests.
  - **5b MiFID XSD gate — Completed** ([`phase-5b-plan.md`](phase-5b-plan.md)): pre-submit schema validation, internal contract XSD, fail-closed orchestrator gate.
  - **5c Production submission — Completed** ([`phase-5c-plan.md`](phase-5c-plan.md)): honest local-outbox default, optional real SFTP, submission event linkage fix, idempotent resubmit guard.
  - **5d ACK hardening — Completed** ([`phase-5d-plan.md`](phase-5d-plan.md)): ACK drop-zone poller, typed submission lookup, sentinel orphan correlation, idempotent ACK ingestion.
  - **Deferred (future 5e/5f or parallel track):** Real AIFMD Annex IV XML and EMIR ISO 20022 templates + XSD validators + golden tests — same pattern as 5a/5b. Submission (5c) and ACK (5d) already work for any regime; only file generation quality differs. Stub renderers remain in `AifmdXmlFilingRenderer` / `EmirIso20022FilingRenderer`.

### Phase 6: Step 5 - Exception Operations and Immutable Audit

- **Goal**: Operationalize rejection handling, correction/resubmission, and complete regulator-grade auditability.
- **Key Deliverables**: Central exception queue, automated/manual correction workflows, immutable cross-pipeline event log, regulator-ready audit export.
- **Dependencies**: Phase 5 (MiFID path — 5a–5d complete)
- **Status**: Completed
- **Notes**:
  - **6a Exception queue — Completed** ([`phase-6a-plan.md`](phase-6a-plan.md)): read-model queue over validation/rule/filing/ACK failure events; `GET /demo/exceptions` with filters.
  - **6b Resolution/resubmit — Completed** ([`phase-6b-plan.md`](phase-6b-plan.md)): immutable `ExceptionResolvedEvent` / `ExceptionResubmitRequestedEvent`; dismiss + Step 4 resubmit with `forceResubmit`; REST resolve/resubmit endpoints.
  - **6c Audit export — Completed** ([`phase-6c-plan.md`](phase-6c-plan.md)): `AuditExportService`, `GET /demo/audit/export` with filters and download headers; full pipeline timeline including resolution events.

### Phase 7: End-to-End Hardening and Go-Live Readiness

- **Goal**: Prove production readiness across correctness, reliability, and compliance evidence.
- **Key Deliverables**: Regime-specific E2E tests and rejection simulations, performance/replay/recovery hardening, traceability matrix to FCA/ESMA sources.
- **Dependencies**: Phases 1-6
- **Status**: Not Started

### Phase 8: Controlled Rollout and Iterative Expansion

- **Goal**: Launch in a controlled scope and expand safely without architecture sprawl.
- **Key Deliverables**: Initial rollout plan, production feedback loop for rule/template refinement, incremental coverage expansion plan.
- **Dependencies**: Phase 7
- **Status**: Not Started
