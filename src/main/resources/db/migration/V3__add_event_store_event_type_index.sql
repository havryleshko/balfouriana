create index if not exists idx_event_store_event_type on event_store (event_type);
create index if not exists idx_event_store_event_type_occurred_at on event_store (event_type, occurred_at);
