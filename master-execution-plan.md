# Balforiana - Master Execution Plan

## Overview

- This plan tracks delivery of a correctness-first regulatory reporting pipeline that produces regulator-ready filings with an immutable audit trail.
- Execution is strictly sequential by phase so each stage is production-viable before the next begins, with no architectural sprawl.
- Source of truth: `design.md`
- Architecture constraint: One Spring Boot app, minimal modules

## Phases

### Phase 1: Foundation and Platform Skeleton

- **Goal**: Get a clean, working Kotlin + Spring Boot project with proper structure and shared primitives.
- **Key Deliverables**: Project structure, domain event contracts, basic persistence, config, logging.
- **Dependencies**: None
- **Status**: Not Started

### Phase 2: Step 1 - Data Hand-off and Canonical Ingestion

- **Goal**: Build reliable ingress that accepts fund/custodian data and normalizes it once into immutable internal events.
- **Key Deliverables**: SFTP/REST/drop-zone ingestion, parsers (CSV first), canonical event mapping, provenance capture, malformed-input rejection flow.
- **Dependencies**: Phase 1
- **Status**: Not Started

### Phase 3: Step 2 - Validation and Mapping

- **Goal**: Validate and enrich canonical events to produce a clean, regulator-safe stream with full decision traceability.
- **Key Deliverables**: Versioned validation packs (schema + business), enrichment adapters (LEI/instrument/venue), exception metadata, immutable validation logs.
- **Dependencies**: Phase 2
- **Status**: Not Started

### Phase 4: Step 3 - Rule Application and Calculations

- **Goal**: Apply deterministic regulatory rules and calculations for MiFID II, AIFMD II, and EMIR.
- **Key Deliverables**: Versioned Kotlin rule sets, calculation components, filing-ready enriched events, end-to-end traceability for computed fields.
- **Dependencies**: Phase 3
- **Status**: Not Started

### Phase 5: Step 4 - Filing Generation and Submission

- **Goal**: Generate correct regulator-ready outputs and submit them through supported channels.
- **Key Deliverables**: Versioned filing templates (MiFID/AIFMD XML, EMIR ISO 20022), submission orchestrators (SFTP first), ACK/NACK ingestion and linkage.
- **Dependencies**: Phase 4
- **Status**: Not Started

### Phase 6: Step 5 - Exception Operations and Immutable Audit

- **Goal**: Operationalize rejection handling, correction/resubmission, and complete regulator-grade auditability.
- **Key Deliverables**: Central exception queue, automated/manual correction workflows, immutable cross-pipeline event log, regulator-ready audit export.
- **Dependencies**: Phase 5
- **Status**: Not Started

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

