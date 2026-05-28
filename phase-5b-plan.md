# Balforiana - Phase 5b Detailed Plan

## Purpose

Phase 5b adds MiFID pre-submit schema validation: rendered XML is checked against a versioned internal contract XSD before `FilingGeneratedEvent` and submission.

## Design Guardrails (Must Hold)

- Source of truth: `design.md`
- Reuse `FilingGenerationFailedEvent` for validation failures
- Fail closed: invalid XML never reaches submission
- MiFID-first; AIFMD/EMIR skip gate until template sub-phases land

## Phase 5b Scope

- In scope: `FilingSchemaValidator` registry, MiFID contract XSD, orchestrator gate, config flag, tests.
- Out of scope: production SFTP (5c), ACK hardening (5d), official ARM XSD swap, AIFMD/EMIR validators.

## Deliverables (completed)

### 5b.0 Validation contracts and config

- `FilingSchemaValidationResult` / `FilingSchemaValidationError`
- `FilingSchemaValidator` interface + `FilingSchemaValidatorRegistry`
- `balfouriana.filing.step4.schema-validation.enabled` config

### 5b.1 MiFID contract XSD and validator

- `src/main/resources/filing/mifid/schemas/mifir-transaction-report-2026.05.18.xsd`
- `MifidFilingTemplatePack.SCHEMA_RESOURCE_PATH` / `SCHEMA_VERSION`
- `MifidFilingSchemaValidator`

### 5b.2 Orchestrator gate

- `SubmissionOrchestratorService` validates after render, before `FilingGeneratedEvent`
- Failure emits `FILING_SCHEMA_VALIDATION_FAILED`

### 5b.3 Tests

- `MifidFilingSchemaValidatorTest` (golden pass, invalid XML fail)
- `SubmissionOrchestratorServiceTest` (fail closed on invalid schema)
- Integration tests updated with complete MiFID fields

## Acceptance Criteria

- Valid 5a MiFID XML passes XSD and submits
- Invalid XML produces `FilingGenerationFailedEvent`; no submission events
- Config can disable validation for local dev
- `./gradlew test` passes

## Follow-on

- **5c** — production SFTP, submission event ID linkage
- **5d** — ACK/NACK hardening
- AIFMD/EMIR templates + XSD validators
