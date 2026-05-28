# Balforiana - Phase 5a Detailed Plan

## Purpose

Phase 5a delivers MiFID filing generation (templates and field mapping): unified regime selection, MiFID field passthrough at ingress, versioned MiFID XML templates with explicit canonical-to-element maps, and golden-file renderer tests.

## Design Guardrails (Must Hold)

- Source of truth: `design.md`
- One Spring Boot app; extend `service/filing` only
- Immutable events; no submission/ACK changes in 5a
- Versioned templates with authority metadata

## Phase 5a Scope

- In scope: `RegulatoryRegimeSelector`, MiFID trade column passthrough, `MifidFilingTemplatePack`, `MifidXmlFilingRenderer`, golden-file tests.
- Out of scope: XSD pre-submit validation (5b), production SFTP (5c), ACK hardening (5d), AIFMD/EMIR templates.

## Deliverables (completed)

### 5a.0 Prerequisites

- `RegulatoryRegimeSelector` (EMIR > MiFID > AIFMD) wired in Steps 2, 3, and 4
- MiFID trade columns passthrough in `CanonicalRecordMapper`
- Regime inference on `CanonicalRecordMappedEvent` in `IngestionParseAndMapService`
- Integration tests for MiFID CSV ingest → MiFID rule pack and filing-ready fields

### 5a.1 MiFID template pack and renderer

- `FilingTemplateContracts` / `MifidFilingTemplatePack` (`step4-mifid-xml` / `2026.05.18`)
- `MifidXmlFilingRenderer` — `MiFIRTransactionReport` with named elements

### 5a.2 Golden-file tests

- `src/test/resources/filing/mifid/filing-ready-input.json`
- `src/test/resources/filing/mifid/expected-transaction-report.xml`
- `MifidXmlFilingRendererGoldenTest`

## Acceptance Criteria

- MiFID CSV ingest uses `step3-mifid-transaction-rules` and populates MiFID filing-ready fields
- Renderer output matches golden file; no `<field name="...">` placeholder tags
- `./gradlew test` passes

## Follow-on

- AIFMD / EMIR template sub-phases
- 5b XSD validation, 5c production SFTP, 5d ACK/NACK hardening
