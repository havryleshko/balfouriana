create table if not exists ingestion_artifact (
    artifact_id uuid primary key,
    correlation_id uuid not null,
    channel varchar(32) not null,
    original_filename varchar(512) not null,
    stored_path varchar(1024) not null,
    byte_size bigint not null,
    received_at timestamp with time zone not null
);

create index if not exists idx_ingestion_artifact_correlation_id on ingestion_artifact (correlation_id);
