# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/3.5.13/gradle-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/3.5.13/gradle-plugin/packaging-oci-image.html)
* [Spring Web](https://docs.spring.io/spring-boot/3.5.13/reference/web/servlet.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/3.5.13/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Validation](https://docs.spring.io/spring-boot/3.5.13/reference/io/validation.html)
* [Flyway Migration](https://docs.spring.io/spring-boot/3.5.13/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Validation](https://spring.io/guides/gs/validating-form-input/)

### Additional Links
These additional references should also help you:

* [Gradle Build Scans – insights for your project's build](https://scans.gradle.com#gradle)

### Ingestion (Phase 2.1)

The app accepts files over **HTTPS** (`POST /ingest` as `multipart/form-data` field `file`) and a **filesystem drop zone** under `${balfouriana.ingestion.root}` (default `./data/ingest`): drop files into `incoming/` and they are picked up after a stability window (`balfouriana.ingestion.drop-zone.stability-check-ms`). Processed raw bytes land under `received/` with collision-safe names; failed drop-zone transfers are moved to `failed/`.

**SFTP:** there is **no embedded SFTP server** in this release. Custodian SFTP should **terminate outside the app** (gateway, bastion, or provider) and write into the **same `incoming/` directory** the drop-zone poller watches. Optional API key: set `BALFOURIANA_INGESTION_API_KEY` and send header `X-Ingestion-Api-Key` on `/ingest`.

### Filing submission (Phase 5c)

Step 4 submission defaults to **local outbox** (`BALFOURIANA_STEP4_SUBMISSION_MODE=LOCAL_OUTBOX`). Rendered filing artifacts are written to `${balfouriana.filing.step4.submission.local-outbox-dir}` (default `./data/filing/outbox`); an external process can pick them up for regulator delivery—same pattern as ingest SFTP termination outside the app.

Optional in-app SFTP upload when configured:

| Variable | Purpose |
|----------|---------|
| `BALFOURIANA_STEP4_SUBMISSION_MODE=SFTP` | Enable real SFTP upload |
| `BALFOURIANA_STEP4_SFTP_HOST` | SFTP host (required in SFTP mode) |
| `BALFOURIANA_STEP4_SFTP_PORT` | Port (default 22) |
| `BALFOURIANA_STEP4_SFTP_USERNAME` | Username |
| `BALFOURIANA_STEP4_SFTP_PASSWORD` | Password (or use private key) |
| `BALFOURIANA_STEP4_SFTP_PRIVATE_KEY_PATH` | Path to private key file |
| `BALFOURIANA_STEP4_SFTP_REMOTE_DIR` | Remote directory (default `/outbound`) |

Duplicate processing of the same filing-ready fingerprint (correlation + checksum + template version) does not re-upload; submission events link to the correct `FilingGeneratedEvent` for audit and ACK linkage.

### Filing acknowledgements (Phase 5d)

ARM/TR acknowledgement files are ingested from a **filesystem drop zone** under `${balfouriana.filing.step4.acknowledgement.drop-zone.root}` (default `./data/filing/ack`). Drop CSV or XML files into `incoming/`; the poller picks them up after a stability window.

**SFTP:** same pattern as ingest and submission — external SFTP terminates outside the app and writes into `ack/incoming/`.

| Variable | Purpose |
|----------|---------|
| `BALFOURIANA_STEP4_ACK_DROP_ZONE_ENABLED` | Enable poller (default true) |
| `BALFOURIANA_STEP4_ACK_ROOT` | ACK drop-zone root (default `./data/filing/ack`) |
| `BALFOURIANA_STEP4_ACK_POLL_MS` | Poll interval (default 5000) |
| `BALFOURIANA_STEP4_ACK_STABILITY_MS` | Stability window before pickup (default 2000) |
| `BALFOURIANA_STEP4_ACK_CHANNEL` | Channel recorded on ACK events (default `SFTP`) |

**CSV format** (header required):

```
external_reference,status,reason_code,message
<submissionId>,ACK,,accepted
```

`external_reference` must match the `submissionId` from `FilingSubmittedEvent`. Unlinked ACKs are grouped under a sentinel correlation ID and queryable via `ValidationAuditQueryService.unresolvedAcknowledgements()`. Duplicate ACK rows for the same submission+status are suppressed.

### Exception queue (Phase 6a)

Open exceptions from Steps 2–4 (validation, rules, filing failures, NACK/orphan ACKs) are queryable via **`GET /demo/exceptions`**. Optional query params: `limit`, `severity` (`BLOCKING` | `NEEDS_REVIEW` | `OPS`), `sourceStep`, `correlationId`, `includeResolved` (default `false`).

| Variable | Purpose |
|----------|---------|
| `BALFOURIANA_EXCEPTION_QUEUE_LOOKBACK_HOURS` | How far back to scan (default 168) |
| `BALFOURIANA_EXCEPTION_QUEUE_LIMIT` | Default max rows (default 100) |

Resolved items are excluded from the default list; set `includeResolved=true` for audit/debug views.

### Exception resolution and resubmit (Phase 6b)

Operators can close the exception loop with immutable resolution events:

| Endpoint | Purpose |
|----------|---------|
| `POST /demo/exceptions/{queueItemId}/resolve` | Dismiss or acknowledge a queue item |
| `POST /demo/exceptions/{queueItemId}/resubmit` | Retry Step 4 submission after NACK/submission failure |

**Resolve body:** `{ "resolutionType": "DISMISSED" \| "ACKNOWLEDGED", "note": "...", "resolvedBy": "operator" }`

**Resubmit body:** `{ "note": "...", "resolvedBy": "operator" }`

Resubmit requires an existing `FilingReadyRecordEvent` for the correlation (same filing-ready artifact from the original pipeline run). To fix upstream validation/rule blocking errors, re-ingest corrected source data via **`POST /ingest`** — there is no in-app field correction.

`forceResubmit` on the submission orchestrator bypasses the Phase 5c duplicate-submit guard so operators can retry after NACK without changing the filing-ready fingerprint.

### Audit export (Phase 6c)

Regulator-ready JSON export of the full immutable event timeline (ingest → validation → rules → filing → ops resolution/resubmit):

**`GET /demo/audit/export`** — returns `AuditExportBundle` with summary + ordered timeline. Response includes `Content-Disposition: attachment` for one-click download.

| Query param | Purpose |
|-------------|---------|
| `correlationId` | Primary key — full pipeline for one run |
| `submissionId` | Resolve correlation from `FilingSubmittedEvent` |
| `artifactId` | Resolve correlation (400 if ambiguous) |
| `regime` | Filter events by `MIFID_II`, `AIFMD_II`, or `EMIR` |
| `start` / `end` | ISO-8601 window for bulk export (requires both when no correlation/submission/artifact) |
| `limit` | Max correlations in window export (default 500, max 5000) |

Example: `GET /demo/audit/export?correlationId=<uuid>`

| Variable | Purpose |
|----------|---------|
| `BALFOURIANA_AUDIT_EXPORT_DEFAULT_LIMIT` | Default max rows/correlations (default 500) |
| `BALFOURIANA_AUDIT_EXPORT_MAX_LIMIT` | Hard cap (default 5000) |
| `BALFOURIANA_AUDIT_EXPORT_LOOKBACK_HOURS` | Default lookback for window exports (default 168) |

