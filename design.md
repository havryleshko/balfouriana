# Balforiana — Regulatory Reporting Engine — design.md

## Design Constitution

We build correctness-critical financial infrastructure that regulators and small funds can trust on day one.  
Every decision prioritizes **trust through simplicity** over cleverness or speed-to-demo.

1. **Clarity over density** — Code, data models, and processes must be readable by a compliance officer who is not a Kotlin expert. We never sacrifice auditability for brevity.  
2. **Correctness by construction** — Kotlin’s type system and sealed classes are non-negotiable; runtime surprises are unacceptable when fines are on the line.  
3. **Simple thing that works** — One Spring Boot app, one database, five clean modules. No microservices, no premature abstraction until we have paying customers.  
4. **Incremental reality** — We research and implement one outsourced step at a time so the system is production-ready after every module.  
5. **Outcome, not software** — The product’s job is to hand back regulator-ready filings and an immutable audit trail. Everything else is scaffolding.

## Core Domain Principles

- All domain models are immutable data classes or sealed interfaces.  
- Every state change is an event-sourced record (never mutated).  
- External data is normalised once on ingress and never again.  
- fin-regbase remains the single source of regulatory truth (called via clean REST or embedded rules).

## Pipeline Architecture (the five outsourced steps)

We replace the exact process small funds currently outsource. Each step becomes one Kotlin module.

### 1. Data Hand-off (verified April 2026 reality)

Small/mid-sized UK/EU funds and AIFMs push daily/periodic extracts from custodians, brokers, and existing admin platforms.  

**Channels**  
- SFTP drop folders (dominant method used by Apex, Langham Hall, State Street, Northern Trust).  
- Secure HTTPS API portals (growing but still secondary).  
- Occasional encrypted email or manual shares for funds <£100m AUM.

**Formats**  
- Primary: CSV (trades, positions, cash movements, corporate actions).  
- Secondary: JSON, FIX fragments, occasional XML.  
- Data is always dirty: missing LEIs, inconsistent ISIN/CFI, mixed date formats, partial corporate-action flags.

**Frequency & Volume**  
- MiFID II/MiFIR: daily T+1 to FCA Market Data Processor (or ARMs).  
- AIFMD II Annex IV: quarterly core filing + daily position snapshots during reporting windows (full expansion April 2027).  
- EMIR: near real-time to Trade Repositories.  
- Typical £200m AUM fund: 5k–50k+ trade lines/day + EOD holdings.

**Real pain the fund feels**  
Multiple custodian logins, manual exports or clunky scripts, constant data-quality arguments with the provider, and legal responsibility under SYSC 8 even when outsourced.

**Our replacement**  
Single secure inbox (SFTP endpoint + REST `/ingest` + simple drop zone).  
Incoming files are immediately parsed and normalised into internal sealed domain events. No further mapping happens downstream.

This step is now research-complete and locked.

### 2. Validation & Mapping (verified 19 April 2026)

This is the highest-effort, highest-error stage in the current outsourced process. Administrators and regtech providers spend the majority of their time here because incoming data is consistently incomplete, non-standardised, and non-compliant with strict regulatory schemas.

**What actually happens today**  
- Raw files arrive (mostly CSV, some JSON/FIX).  
- Two validation layers run in parallel: syntax/schema validation against the latest FCA and ESMA validation rule files, and business/content validation (LEI status via GLEIF, CFI code validity, instrument type vs venue MIC, price multiplier > 0, notional currency ISO codes, cross-field logic, event date vs reporting timestamp rules, etc.).  
- Mapping/enrichment happens simultaneously: instrument master lookup, LEI resolution, venue MIC standardisation, and (under AIFMD II) new delegation/LMT/loan origination data from HR, compliance, and portfolio systems.  
- Exceptions are manually reviewed, fixed, or sent back to the fund.

**Top pain points & rejection drivers (2026 reality)**  
- LEI issues (wrong status, branch vs entity confusion, missing LEIs for execution agents).  
- Instrument identification (missing/incorrect CFI codes, ISIN vs OTC fallback logic).  
- Numeric/formatting errors (price multiplier, notional currency, precision rules).  
- Cross-field & conditional logic (collateral portfolio code, cleared status, event date vs timestamp).  
- AIFMD II specific (new mandatory fields on delegation %, internal/delegated FTEs, LMT usage, loan origination details).  
- EMIR Refit impact (203 fields; Phase II reconciliation on 27 April 2026 EU adds 61 new fields, many with zero tolerance).

**Our Step 2 module must deliver**  
- Ingest raw files → normalise immediately to internal canonical model.  
- Run full schema + business validation using versioned FCA/ESMA validation rule packs.  
- Perform intelligent enrichment/mapping (GLEIF LEI lookup, FIRDS instrument enrichment, CFI derivation).  
- Apply tolerance rules exactly where regulators allow them.  
- Produce a clean, validated internal event stream with rich exception metadata.  
- Log every validation decision immutably for audit.

This step is now research-complete and locked.

### 3. Rule Application & Calculation (verified 19 April 2026)

This is the intelligence layer. After data is validated and enriched (Step 2), we apply the actual regulatory business rules and calculations.

**Key rules (kept practical)**

**MiFID II / MiFIR**  
- Best execution: “all sufficient steps” to achieve best overall outcome (price, costs, speed, likelihood of execution/settlement, size, nature…).  
- Required flags & classification: buyer/seller & decision maker (LEI rules), execution within firm (human vs algorithm), venue of execution, short selling flag, waiver/OTC indicators, commodity derivative indicator, price notation/conditions.

**AIFMD II (live 16 April 2026)**  
- Leverage: commitment method primary (gross method also supported). Exposure = sum of absolute values of positions with specific derivative and borrowing treatment.  
- New Loan-Originating AIF (LOF) limits: open-ended max 175% of NAV, closed-ended max 300% of NAV (commitment method).  
- New required calculations: delegation %, internal vs delegated FTEs, LMT usage, loan origination concentration (20% borrower limit), 5% risk retention.

**EMIR**  
- Cleared status logic (Y/N + clearing member LEI if cleared).  
- Collateral/portfolio code logic and margin calculations.  
- Lifecycle event sequencing and valuation updates.  
- Phase II reconciliation fields (27 April 2026 EU) with strict matching rules.

**Our Step 3 module (simple design)**  
- Takes clean validated data from Step 2.  
- Applies deterministic, versioned Kotlin business rules (no over-engineered rules engine).  
- Performs required calculations and flag logic.  
- Produces fully enriched events with clear audit trail.  
- Only flags genuine edge cases for human review.  
- Outputs ready for filing generation (Step 4).

This step is now research-complete and locked.

### 4. Filing Generation & Submission (verified 19 April 2026)

This is the final output layer. After rules and calculations are applied (Step 3), we generate the exact regulator-ready files and submit them.

**What actually happens today**  
- Enriched data is turned into the required file formats:  
  - MiFID II/MiFIR: XML (primary) or CSV to Approved Reporting Mechanisms (ARMs) such as DTCC GTR UK MiFID ARM (production go-live 18 May 2026).  
  - AIFMD II Annex IV: XML per ESMA technical guidance (some NCAs, e.g. Norway, made XML mandatory from 30 June 2026). Full harmonised EU schema expected 2027.  
  - EMIR: ISO 20022 XML to Trade Repositories (DTCC GTR, REGIS-TR, etc.).  
- Submission methods: SFTP (dominant), MQ, Web Upload, and some APIs.  
- Files must pass strict schema + business validation before acceptance.

**Our Step 4 module (simple design)**  
- Take fully enriched events from Step 3.  
- Generate correct, validated XML/ISO 20022 files for each regime (versioned templates).  
- Support multiple submission channels (SFTP primary, API where available).  
- Handle acknowledgements and rejection files from ARMs/TRs.  
- Maintain complete audit of what was sent, when, and the response received.

This step is now research-complete and locked.

### 5. Error Handling & Audit (verified 19 April 2026)

This is the final safety net. Even with perfect upstream steps, rejections happen. Step 5 must catch them, enable fast correction/resubmission, and maintain an immutable, regulator-grade audit trail for every action across the entire pipeline.

**Key requirements (kept practical)**

**Error Handling**  
- MiFID II (ARMs like DTCC GTR): Near-real-time validation, Corrections Engine for easy exception management and direct resubmission, ACK/NACK with intra-day + end-of-day reports.  
- EMIR (Trade Repositories): Immediate rejection response + end-of-day reports with detailed error codes. Phase II (April/Sept 2026) increases reconciliation breaks — requires structured exception management.  
- AIFMD II Annex IV: Stricter validation + new delegation/LMT/loan data increases rejection risk. Needs robust cross-system governance and audit evidence for delegation oversight.

**Audit Trail**  
- Immutable, complete audit trail is mandatory for all regimes.  
- Must cover every data change, validation decision, calculation, file generation, submission, acknowledgement, correction, and resubmission.  
- Regulators expect full traceability — “when the regulator asks, you have the answer.”  
- Event-sourced design (already in our principles) is perfect.

**Our Step 5 module (simple design)**  
- Centralized exception queue with rich metadata from Steps 2–4.  
- Automated resubmission workflows for straightforward corrections.  
- Immutable event log for every action across the pipeline.  
- Clear human-in-the-loop only for genuine complex exceptions.  
- One-click regulator-ready audit export (full history, filters by filing/regime/date).

This step ensures the entire system is defensible and regulator-ready at all times.

---

## Do’s, Don’ts & Anti-Patterns

**Do**  
- Normalise at the edge (ingestion module only).  
- Maintain versioned validation rule packs, business rules, and file templates with clear change history.  
- Log every validation, calculation, file, submission, and correction immutably.  
- Produce rich exception metadata so human review is fast and targeted.

**Don’t**  
- Ever mutate data after Step 2 normalisation.  
- Hide errors behind generic messages.  
- Over-engineer the rule engine or exception handling — keep it clean and testable.

## Cursor / Agent Instructions

When editing this codebase:  
- Always reference @design.md before proposing changes.  
- Prefer one module per step.  
- Use data classes + sealed interfaces for domain models.  
- Keep the Spring Boot app minimal until we have the first paying customer.  
- If something feels clever, delete it.  
- Every rule, template, and audit decision must be traceable to an official FCA or ESMA source with date. 

## Recommended structure - balanced.

balforiana/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/balforiana/
│   │   │       ├── BalforianaApplication.kt
│   │   │       ├── config/
│   │   │       ├── domain/           ← Core models + business rules
│   │   │       ├── service/          ← Business logic (simpler than application/)
│   │   │       ├── repository/       ← Database access
│   │   │       └── api/              ← REST controllers
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/
│   └── test/
│       └── kotlin/...
├── build.gradle.kts
├── settings.gradle.kts
└── README.md