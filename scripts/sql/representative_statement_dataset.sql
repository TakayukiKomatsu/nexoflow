-- representative_statement_dataset.sql
-- Deterministic data-only seed for settlement-statement EXPLAIN evidence.
--
-- The schema is intentionally absent from this file. The evidence runner starts
-- the real backend first, so Flyway applies every production SQL and Java
-- migration before these rows are inserted inside BEGIN ... ROLLBACK.
--
-- Dataset shape:
--   10      assignors
--   10,000  receivables, pricing quotes, settlements, and settlement items
--   1,000   reversals (every tenth settlement)
--
-- Dimensions are deliberately selective:
--   assignor_id              cycles through 10 assignors
--   asset_currency_code      alternates BRL/USD
--   settlement_currency_code follows a four-row BRL/USD pattern
--   product_type_code        uses both supported product types

do $$
begin
    if to_regclass('public.flyway_schema_history') is null then
        raise exception 'Flyway schema history is absent; production migrations were not applied';
    end if;
    if exists (select 1 from settlements)
       or exists (select 1 from settlement_items)
       or exists (select 1 from settlement_reversals)
       or exists (select 1 from pricing_quotes)
       or exists (select 1 from receivables)
       or exists (select 1 from assignors) then
        raise exception 'representative evidence requires an empty financial dataset';
    end if;
end
$$;

insert into assignors (
    id,
    legal_name,
    normalized_tax_id,
    active,
    created_at,
    created_by
)
select md5('rep:assignor:' || n::text)::uuid,
       'Representative Assignor ' || lpad(n::text, 2, '0'),
       lpad(n::text, 14, '0'),
       true,
       timestamp '2030-01-01 00:00:00',
       'reporting-evidence@srm.local'
from generate_series(1, 10) n;

insert into receivables (
    id,
    assignor_id,
    product_type_code,
    face_currency_code,
    face_amount,
    issue_date,
    due_date,
    status,
    version,
    created_at,
    created_by
)
select md5('rep:receivable:' || n::text)::uuid,
       md5('rep:assignor:' || (((n - 1) % 10) + 1)::text)::uuid,
       case when n % 3 = 2
            then 'POST_DATED_CHEQUE'
            else 'MERCANTILE_INVOICE'
       end,
       case when n % 2 = 0 then 'USD' else 'BRL' end,
       (1000 + (n % 100))::numeric(19,4),
       date '2030-01-01',
       date '2030-02-14',
       'SETTLED',
       1,
       timestamp '2030-01-01 00:00:00',
       'reporting-evidence@srm.local'
from generate_series(1, 10000) n;

insert into pricing_quotes (
    id,
    receivable_id,
    settlement_currency_code,
    face_amount,
    face_currency_code,
    product_type_code,
    due_date,
    pricing_at,
    expires_at,
    base_rate,
    spread,
    strategy_code,
    day_count_convention,
    term_in_months,
    discounted_amount,
    fx_base_currency_code,
    fx_quote_currency_code,
    fx_rate,
    fx_source,
    fx_observed_at,
    settlement_amount,
    created_by,
    status
)
select md5('rep:quote:' || n::text)::uuid,
       md5('rep:receivable:' || n::text)::uuid,
       case when n % 4 in (0, 1) then 'USD' else 'BRL' end,
       (1000 + (n % 100))::numeric(19,4),
       case when n % 2 = 0 then 'USD' else 'BRL' end,
       case when n % 3 = 2
            then 'POST_DATED_CHEQUE'
            else 'MERCANTILE_INVOICE'
       end,
       date '2030-02-14',
       timestamp '2030-01-01 04:00:00' + (((n - 1) % 30) || ' days')::interval,
       timestamp '2030-01-01 04:15:00' + (((n - 1) % 30) || ' days')::interval,
       0.1200000000::numeric(19,10),
       0.0200000000::numeric(19,10),
       'STANDARD',
       'ACT360',
       1.5000000000::numeric(19,10),
       (950 + (n % 100))::numeric(19,4),
       case when n % 2 = 0 then 'USD' else 'BRL' end,
       case when n % 4 in (0, 1) then 'USD' else 'BRL' end,
       1.0000000000::numeric(19,10),
       'REPORTING_EVIDENCE',
       timestamp '2030-01-01 03:00:00' + (((n - 1) % 30) || ' days')::interval,
       (950 + (n % 100))::numeric(19,4),
       'reporting-evidence@srm.local',
       'CONSUMED'
from generate_series(1, 10000) n;

insert into settlements (
    id,
    assignor_id,
    settlement_currency_code,
    total_amount,
    status,
    created_at,
    created_by
)
select md5('rep:settlement:' || n::text)::uuid,
       md5('rep:assignor:' || (((n - 1) % 10) + 1)::text)::uuid,
       case when n % 4 in (0, 1) then 'USD' else 'BRL' end,
       (950 + (n % 100))::numeric(19,4),
       'COMPLETED',
       timestamp '2030-01-01 06:00:00' + (((n - 1) % 30) || ' days')::interval,
       'reporting-evidence@srm.local'
from generate_series(1, 10000) n;

insert into settlement_items (
    id,
    settlement_id,
    quote_id,
    receivable_id,
    item_position,
    settlement_amount,
    asset_currency_code,
    product_type_code
)
select md5('rep:item:' || n::text)::uuid,
       md5('rep:settlement:' || n::text)::uuid,
       md5('rep:quote:' || n::text)::uuid,
       md5('rep:receivable:' || n::text)::uuid,
       1,
       (950 + (n % 100))::numeric(19,4),
       case when n % 2 = 0 then 'USD' else 'BRL' end,
       case when n % 3 = 2
            then 'POST_DATED_CHEQUE'
            else 'MERCANTILE_INVOICE'
       end
from generate_series(1, 10000) n;

insert into settlement_reversals (
    id,
    settlement_id,
    reason,
    reversed_at,
    reversed_by
)
select md5('rep:reversal:' || n::text)::uuid,
       md5('rep:settlement:' || n::text)::uuid,
       'Representative evidence reversal for settlement ' || n,
       timestamp '2030-01-01 08:00:00' + (((n - 1) % 30) || ' days')::interval,
       'reporting-evidence@srm.local'
from generate_series(10, 10000, 10) n;

analyze assignors;
analyze receivables;
analyze pricing_quotes;
analyze settlements;
analyze settlement_items;
analyze settlement_reversals;
