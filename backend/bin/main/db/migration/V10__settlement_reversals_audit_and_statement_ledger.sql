create table settlement_reversals (
    id uuid primary key,
    settlement_id uuid not null unique references settlements(id),
    reason varchar(500) not null check (length(trim(reason)) > 0),
    reversed_at timestamp not null,
    reversed_by varchar(200) not null
);

alter table idempotency_records add column reversal_id uuid references settlement_reversals(id);
alter table idempotency_records drop constraint idempotency_records_state_check;
alter table idempotency_records add constraint idempotency_records_state_check check (
    (status = 'PROCESSING' and settlement_id is null and reversal_id is null and completed_at is null)
    or (status = 'COMPLETED' and ((settlement_id is not null and reversal_id is null) or (settlement_id is null and reversal_id is not null)) and completed_at is not null)
);

alter table settlement_items add column asset_currency_code varchar(3);
alter table settlement_items add column product_type_code varchar(50);
update settlement_items i set asset_currency_code = r.face_currency_code, product_type_code = r.product_type_code
from receivables r where r.id = i.receivable_id;
alter table settlement_items alter column asset_currency_code set not null;
alter table settlement_items alter column product_type_code set not null;
alter table settlement_items add constraint settlement_items_asset_currency_fk foreign key (asset_currency_code) references currencies(code);
alter table settlement_items add constraint settlement_items_product_type_fk foreign key (product_type_code) references product_types(code);

create table audit_events (
    id uuid primary key,
    actor varchar(200) not null,
    action varchar(80) not null,
    target_type varchar(80) not null,
    target_id uuid not null,
    occurred_at timestamp not null,
    correlation_id varchar(100),
    safe_metadata jsonb not null default '{}'::jsonb
);
create index audit_events_target_idx on audit_events(target_type, target_id, occurred_at desc);
create index settlement_reversals_settlement_idx on settlement_reversals(settlement_id);
create index settlements_created_at_idx on settlements(created_at desc, id desc);
create index settlement_items_statement_dimensions_idx on settlement_items(asset_currency_code, product_type_code, settlement_id);
