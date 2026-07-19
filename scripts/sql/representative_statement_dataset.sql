-- representative_statement_dataset.sql
-- Deterministic, transaction-scoped dataset for SRM settlement-statement EXPLAIN evidence.
-- Run INSIDE BEGIN ... ROLLBACK; all schema and data are discarded after plan capture.
--
-- Dataset shape:
--   10  assignors  (md5 UUIDs from 'rep:assignor:N')
--   10,000  receivables, quotes, settlements, items  (one-to-one chain)
--   1,000   reversals (every 10th settlement: n=10,20,...,10000)
--   Timestamps: settlements span 2030-01-01 to 2030-01-30 (n-1 mod 30 days offset)
--   Query window in explain SQL: 2030-01-10 to 2030-01-20 (~3,334 settlement rows)
--   All rows use settlement_currency_code = 'BRL'.
--
-- UUID derivation: md5('rep:<entity>:<n>')::uuid  — fully deterministic, no sequences.

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. SCHEMA  (mirrors production Flyway migrations V3, V8, V9, V10, V11)
-- ─────────────────────────────────────────────────────────────────────────────

-- V3 — currency and product reference
create table currencies (
    code          varchar(3)  primary key,
    decimal_scale integer     not null,
    active        boolean     not null
);
create table product_types (
    code   varchar(50) primary key,
    active boolean     not null
);

insert into currencies (code, decimal_scale, active) values
    ('BRL', 2, true),
    ('USD', 2, true);
insert into product_types (code, active) values
    ('MERCANTILE_INVOICE', true),
    ('POST_DATED_CHEQUE',  true);

-- V8 — assignors, receivables, pricing_quotes
create table assignors (
    id                 uuid         primary key,
    legal_name         varchar(200) not null,
    normalized_tax_id  varchar(32)  not null unique,
    active             boolean      not null,
    created_at         timestamp    not null,
    created_by         varchar(200) not null
);

create table receivables (
    id                uuid         primary key,
    assignor_id       uuid         not null references assignors(id),
    product_type_code varchar(50)  not null references product_types(code),
    face_currency_code varchar(3)  not null references currencies(code),
    face_amount       numeric(19,4) not null check (face_amount > 0),
    issue_date        date         not null,
    due_date          date         not null check (due_date > issue_date),
    status            varchar(20)  not null check (status in ('REGISTERED','SETTLED','REVERSED')),
    version           bigint       not null default 0,
    created_at        timestamp    not null,
    created_by        varchar(200) not null
);

create table pricing_quotes (
    id                      uuid          primary key,
    receivable_id           uuid          not null references receivables(id),
    settlement_currency_code varchar(3)   not null references currencies(code),
    face_amount             numeric(19,4) not null check (face_amount > 0),
    face_currency_code      varchar(3)    not null references currencies(code),
    due_date                date          not null,
    pricing_at              timestamp     not null,
    expires_at              timestamp     not null check (expires_at > pricing_at),
    base_rate               numeric(19,10) not null,
    spread                  numeric(19,10) not null,
    strategy_code           varchar(50)   not null,
    day_count_convention    varchar(50)   not null,
    term_in_months          numeric(19,10) not null,
    discounted_amount       numeric(19,4) not null check (discounted_amount > 0),
    fx_base_currency_code   varchar(3)    not null references currencies(code),
    fx_quote_currency_code  varchar(3)    not null references currencies(code),
    fx_rate                 numeric(19,10) not null check (fx_rate > 0),
    fx_source               varchar(100)  not null,
    fx_observed_at          timestamp     not null,
    settlement_amount       numeric(19,4) not null check (settlement_amount > 0),
    created_by              varchar(200)  not null,
    status                  varchar(20)   not null default 'ACTIVE'
                            check (status in ('ACTIVE','CONSUMED'))
);
create index pricing_quotes_receivable_id_idx on pricing_quotes(receivable_id);

-- V9 — settlements, settlement_items
create table settlements (
    id                       uuid          primary key,
    assignor_id              uuid          not null references assignors(id),
    settlement_currency_code varchar(3)    not null references currencies(code),
    total_amount             numeric(19,4) not null check (total_amount > 0),
    status                   varchar(20)   not null check (status = 'COMPLETED'),
    created_at               timestamp     not null,
    created_by               varchar(200)  not null
);

create table settlement_items (
    id                   uuid          primary key,
    settlement_id        uuid          not null references settlements(id),
    quote_id             uuid          not null unique references pricing_quotes(id),
    receivable_id        uuid          not null unique references receivables(id),
    item_position        integer       not null check (item_position > 0),
    settlement_amount    numeric(19,4) not null check (settlement_amount > 0),
    asset_currency_code  varchar(3)    not null references currencies(code),
    product_type_code    varchar(50)   not null references product_types(code),
    unique (settlement_id, item_position)
);

-- V10 — settlement_reversals, indexes
create table settlement_reversals (
    id            uuid         primary key,
    settlement_id uuid         not null unique references settlements(id),
    reason        varchar(500) not null check (length(trim(reason)) > 0),
    reversed_at   timestamp    not null,
    reversed_by   varchar(200) not null
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. INDEXES  (exact mirrors of production Flyway V10 indexes)
-- ─────────────────────────────────────────────────────────────────────────────
-- Used by the settlement branch of the statement query (effective_at = s.created_at).
create index settlements_created_at_idx
    on settlements(created_at desc, id desc);

-- Used for the reversal-to-settlement JOIN in the reversal branch.
create index settlement_reversals_settlement_idx
    on settlement_reversals(settlement_id);

-- Supports dimension filters (asset_currency_code, product_type_code).
create index settlement_items_statement_dimensions_idx
    on settlement_items(asset_currency_code, product_type_code, settlement_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. ASSIGNORS  (10 rows)
-- ─────────────────────────────────────────────────────────────────────────────
insert into assignors (id, legal_name, normalized_tax_id, active, created_at, created_by)
select
    md5('rep:assignor:' || n::text)::uuid,
    'Representative Assignor ' || lpad(n::text, 2, '0'),
    lpad(n::text, 14, '0'),
    true,
    timestamp '2030-01-01 00:00:00',
    'rep-seeder'
from generate_series(1, 10) n;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. RECEIVABLES  (10,000 rows)
--    assignor cycles 1..10; product_type alternates; timestamps cycle 30 days
-- ─────────────────────────────────────────────────────────────────────────────
insert into receivables (
    id, assignor_id, product_type_code, face_currency_code,
    face_amount, issue_date, due_date, status, version, created_at, created_by
)
select
    md5('rep:receivable:' || n::text)::uuid,
    md5('rep:assignor:' || (((n - 1) % 10) + 1)::text)::uuid,
    case when n % 2 = 0 then 'MERCANTILE_INVOICE' else 'POST_DATED_CHEQUE' end,
    'BRL',
    (1000 + (n % 100))::numeric(19,4),
    date '2030-01-01' + (((n - 1) % 30) || ' days')::interval,
    date '2030-02-14',
    'SETTLED',
    0,
    timestamp '2030-01-01 00:00:00' + (((n - 1) % 30) || ' days')::interval,
    'rep-seeder'
from generate_series(1, 10000) n;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. PRICING QUOTES  (10,000 rows, status = CONSUMED)
-- ─────────────────────────────────────────────────────────────────────────────
insert into pricing_quotes (
    id, receivable_id, settlement_currency_code,
    face_amount, face_currency_code, due_date,
    pricing_at, expires_at,
    base_rate, spread, strategy_code, day_count_convention,
    term_in_months, discounted_amount,
    fx_base_currency_code, fx_quote_currency_code,
    fx_rate, fx_source, fx_observed_at,
    settlement_amount, created_by, status
)
select
    md5('rep:quote:' || n::text)::uuid,
    md5('rep:receivable:' || n::text)::uuid,
    'BRL',
    (1000 + (n % 100))::numeric(19,4),
    'BRL',
    date '2030-02-14',
    timestamp '2030-01-01 04:00:00' + (((n - 1) % 30) || ' days')::interval,
    timestamp '2030-01-01 05:00:00' + (((n - 1) % 30) || ' days')::interval,
    0.1200::numeric(19,10),
    0.0200::numeric(19,10),
    'STANDARD',
    'ACT360',
    1.5::numeric(19,10),
    (950 + (n % 100))::numeric(19,4),
    'BRL',
    'BRL',
    1.0000::numeric(19,10),
    'rep-fx-identity',
    timestamp '2030-01-01 03:00:00' + (((n - 1) % 30) || ' days')::interval,
    (950 + (n % 100))::numeric(19,4),
    'rep-seeder',
    'CONSUMED'
from generate_series(1, 10000) n;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. SETTLEMENTS  (10,000 rows)
--    created_at = 2030-01-01 06:00 + (n-1 mod 30) days
--    Query window [2030-01-10, 2030-01-20) covers mod values 9..18 → ~3,334 rows
-- ─────────────────────────────────────────────────────────────────────────────
insert into settlements (
    id, assignor_id, settlement_currency_code,
    total_amount, status, created_at, created_by
)
select
    md5('rep:settlement:' || n::text)::uuid,
    md5('rep:assignor:' || (((n - 1) % 10) + 1)::text)::uuid,
    'BRL',
    (950 + (n % 100))::numeric(19,4),
    'COMPLETED',
    timestamp '2030-01-01 06:00:00' + (((n - 1) % 30) || ' days')::interval,
    'rep-seeder'
from generate_series(1, 10000) n;

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. SETTLEMENT ITEMS  (10,000 rows, one per settlement)
-- ─────────────────────────────────────────────────────────────────────────────
insert into settlement_items (
    id, settlement_id, quote_id, receivable_id,
    item_position, settlement_amount,
    asset_currency_code, product_type_code
)
select
    md5('rep:item:' || n::text)::uuid,
    md5('rep:settlement:' || n::text)::uuid,
    md5('rep:quote:' || n::text)::uuid,
    md5('rep:receivable:' || n::text)::uuid,
    1,
    (950 + (n % 100))::numeric(19,4),
    'BRL',
    case when n % 2 = 0 then 'MERCANTILE_INVOICE' else 'POST_DATED_CHEQUE' end
from generate_series(1, 10000) n;

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. REVERSALS  (1,000 rows: every 10th settlement, n = 10, 20, …, 10000)
--    reversed_at = 2030-01-01 08:00 + same day offset as the settlement
-- ─────────────────────────────────────────────────────────────────────────────
insert into settlement_reversals (id, settlement_id, reason, reversed_at, reversed_by)
select
    md5('rep:reversal:' || n::text)::uuid,
    md5('rep:settlement:' || n::text)::uuid,
    'Representative evidence reversal for settlement ' || n,
    timestamp '2030-01-01 08:00:00' + (((n - 1) % 30) || ' days')::interval,
    'rep-seeder'
from generate_series(10, 10000, 10) n;

-- ─────────────────────────────────────────────────────────────────────────────
-- 9. ANALYZE — update planner statistics while data is present in the transaction
--    (statistics are written to system catalogs outside the transaction scope
--     and will remain after ROLLBACK; the tables themselves disappear)
-- ─────────────────────────────────────────────────────────────────────────────
analyze settlements;
analyze settlement_items;
analyze settlement_reversals;
