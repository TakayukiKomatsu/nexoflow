-- explain_settlement_statement.sql
-- Exact production query shape from JdbcSettlementStatementService.java,
-- with literal filter values substituted for the JDBC '?' placeholders.
--
-- Parameterized shape (production):
--   ... where effective_at >= ? and effective_at < ?
--       and settlement_currency_code = ?
--   order by effective_at desc, entry_id desc limit ? offset ?
--   args: [fromTimestamp, toTimestamp, currency, size+1, offset]
--
-- Representative values used here:
--   from   = 2030-01-10 00:00:00   (timestamp, no timezone — matches column type)
--   to     = 2030-01-20 00:00:00
--   currency = 'BRL'
--   limit  = 51  (page size 50 + 1 for hasNext sentinel)
--   offset = 0   (page 0)
--
-- Run INSIDE the BEGIN…ROLLBACK transaction that wraps representative_statement_dataset.sql.

-- ── Row counts after dataset insertion ───────────────────────────────────────
select table_name, row_count
from (values
    ('assignors',            (select count(*) from assignors)),
    ('receivables',          (select count(*) from receivables)),
    ('pricing_quotes',       (select count(*) from pricing_quotes)),
    ('settlements',          (select count(*) from settlements)),
    ('settlement_items',     (select count(*) from settlement_items)),
    ('settlement_reversals', (select count(*) from settlement_reversals))
) t(table_name, row_count)
order by table_name;

-- ── EXPLAIN ANALYZE — exact production SQL shape ──────────────────────────────
explain (analyze, buffers, settings, wal, format text)
select entry_id, entry_type, signed_amount, effective_at, settlement_id, reversal_id,
       assignor_id, asset_currency_code, settlement_currency_code, product_type_code,
       receivable_id
from (
    select md5('SETTLEMENT:' || i.id::text)::uuid entry_id,
           'SETTLEMENT'             entry_type,
           i.settlement_amount      signed_amount,
           s.created_at             effective_at,
           s.id                     settlement_id,
           null::uuid               reversal_id,
           s.assignor_id,
           i.asset_currency_code,
           s.settlement_currency_code,
           i.product_type_code,
           i.receivable_id
      from settlements s
      join settlement_items i on i.settlement_id = s.id
    union all
    select md5('REVERSAL:' || r.id::text || ':' || i.id::text)::uuid entry_id,
           'REVERSAL'               entry_type,
           -i.settlement_amount     signed_amount,
           r.reversed_at            effective_at,
           s.id                     settlement_id,
           r.id                     reversal_id,
           s.assignor_id,
           i.asset_currency_code,
           s.settlement_currency_code,
           i.product_type_code,
           i.receivable_id
      from settlement_reversals r
      join settlements s on s.id = r.settlement_id
      join settlement_items i on i.settlement_id = s.id
) ledger
where effective_at >= timestamp '2030-01-10 00:00:00'
  and effective_at < timestamp '2030-01-20 00:00:00'
  and settlement_currency_code = 'BRL'
order by effective_at desc, entry_id desc
limit 51 offset 0;
