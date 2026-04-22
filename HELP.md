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

