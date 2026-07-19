alter table pricing_quotes add column status varchar(20) not null default 'ACTIVE'
    check (status in ('ACTIVE', 'CONSUMED'));

create table settlements (
    id uuid primary key,
    assignor_id uuid not null references assignors(id),
    settlement_currency_code varchar(3) not null references currencies(code),
    total_amount numeric(19,4) not null check (total_amount > 0),
    status varchar(20) not null check (status = 'COMPLETED'),
    created_at timestamp not null,
    created_by varchar(200) not null
);

create table settlement_items (
    id uuid primary key,
    settlement_id uuid not null references settlements(id),
    quote_id uuid not null unique references pricing_quotes(id),
    receivable_id uuid not null unique references receivables(id),
    item_position integer not null check (item_position > 0),
    settlement_amount numeric(19,4) not null check (settlement_amount > 0),
    unique (settlement_id, item_position)
);

create table idempotency_records (
    id uuid primary key,
    actor varchar(200) not null,
    operation varchar(100) not null,
    idempotency_key varchar(200) not null,
    request_hash char(64) not null,
    settlement_id uuid references settlements(id),
    status varchar(20) not null check (status in ('PROCESSING', 'COMPLETED')),
    created_at timestamp not null,
    completed_at timestamp,
    unique (actor, operation, idempotency_key),
    constraint idempotency_records_state_check check ((status = 'PROCESSING' and settlement_id is null and completed_at is null)
        or (status = 'COMPLETED' and settlement_id is not null and completed_at is not null))
);
