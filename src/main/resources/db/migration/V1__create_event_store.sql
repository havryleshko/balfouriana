create table if not exists event_store (
    event_id uuid primary key,
    correlation_id uuid not null,
    event_type varchar(120) not null,
    source_system varchar(120) not null,
    schema_version varchar(32) not null,
    regimes varchar(255) not null,
    occurred_at timestamp with time zone not null,
    payload text not null,
    created_at timestamp with time zone not null default current_timestamp
);

create index if not exists idx_event_store_correlation_id on event_store (correlation_id);
create index if not exists idx_event_store_occurred_at on event_store (occurred_at);
