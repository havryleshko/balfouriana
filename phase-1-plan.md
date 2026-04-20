# Balforiana - Phase 1 Detailed Plan

## Purpose

Phase 1 establishes a minimal, production-credible foundation for the full reporting pipeline: one Spring Boot app, one database, immutable domain/event primitives, and clean module boundaries that support Steps 1-5 without rework.

## Design Guardrails (Must Hold)

- Source of truth: `design.md`
- One Spring Boot app and one database only
- Simplicity over abstraction; no microservices or speculative frameworks
- Domain state modeled with immutable data classes and sealed interfaces
- Event-sourced persistence model for all state transitions
- No downstream remapping after ingestion normalization in later phases

## Phase 1 Scope

- In scope: platform skeleton, shared contracts, persistence baseline, config baseline, logging/observability baseline, initial quality gates.
- Out of scope: ingestion adapters, validation rule packs, filing templates, submission channel integrations, exception workflows.

## Deliverables

### 1) Project Skeleton and Module Layout

- Create Kotlin + Spring Boot structure aligned to `design.md`:
  - `src/main/kotlin/com/balforiana/config`
  - `src/main/kotlin/com/balforiana/domain`
  - `src/main/kotlin/com/balforiana/service`
  - `src/main/kotlin/com/balforiana/repository`
  - `src/main/kotlin/com/balforiana/api`
  - `src/main/resources`
  - `src/main/resources/db/migration`
  - `src/test/kotlin`
- Establish package conventions and boundaries to prevent cross-module leakage.

### 2) Shared Domain and Event Contracts

- Define core sealed interfaces for canonical event families and pipeline lifecycle events.
- Define immutable data classes for event metadata:
  - event id, correlation id, source system, ingest timestamp, regime tags, schema/version identifiers.
- Define a shared error/decision envelope shape for future validation and audit stages.

### 3) Persistence and Migration Baseline

- Set up migration framework and initial schema.
- Create minimal event store tables for append-only records and metadata indexing.
- Add repository interfaces for write-once event append and read-by-correlation/query primitives.
- Ensure no update-in-place path exists for event records.

### 4) Configuration and Runtime Baseline

- Add environment-aware configuration profile layout.
- Define strict config surface for database, logging level, and app identity only.
- Set secrets-loading approach for local/dev/prod without hardcoded credentials.

### 5) Logging and Observability Baseline

- Add structured logging with correlation id propagation.
- Define log fields required for audit trace stitching from day one.
- Add startup health endpoint and basic readiness/liveness hooks.

### 6) Quality Gates and CI Foundation

- Add unit test scaffold and one integration test path for startup + database migration.
- Add static checks/build gate as required for Kotlin/Spring baseline.
- Ensure baseline pipeline fails fast on schema/migration/test failures.

## Execution Sequence

### Step A - Bootstrap

- Initialize build and project structure.
- Validate app boots with empty domain logic.

### Step B - Domain Contracts

- Add immutable domain/event primitives and envelope types.
- Add tests for serialization stability and immutability assumptions.

### Step C - Persistence

- Introduce migration baseline and event store schema.
- Add append/query repository adapters and persistence tests.

### Step D - Runtime Controls

- Add config profiles, secrets-loading contract, structured logging fields.
- Add health/readiness endpoints.

### Step E - Quality Gate Closure

- Add minimal CI checks and enforce green baseline.
- Freeze Phase 1 outputs for Phase 2 dependency.

## Acceptance Criteria (Definition of Done)

- App boots cleanly in local/dev profiles.
- Migrations run successfully on clean database and are repeatable.
- Core domain contracts are immutable and covered by tests.
- Event writes are append-only; no mutation path exists.
- Structured logs include required correlation and event metadata fields.
- Baseline CI/build/test checks pass consistently.
- Artifacts and boundaries are documented enough to start Phase 2 without architecture changes.

## Dependencies

- Required before start: None
- Blocks for next phase: Phase 2 cannot start until all acceptance criteria above are met.

## Risks and Controls

- Risk: Over-engineering foundation before real flow exists.
  - Control: Keep only shared primitives that are required by at least two downstream phases.
- Risk: Premature modular complexity.
  - Control: Stay within one app and recommended folder boundaries; defer extra layers.
- Risk: Audit gaps introduced early.
  - Control: Enforce correlation/event metadata in contracts and logs at baseline.

## Status Tracker

- Status: Not Started
- Owner: Unassigned
- Target start: TBD
- Target completion: TBD

