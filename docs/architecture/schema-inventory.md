# Schema and DDL inventory

Flyway is the sole schema authority. The Mermaid [ER diagram](er-diagram.mmd)
shows every table and column. This inventory makes constraints, indexes, and
PostgreSQL-only Java migration guards reviewable; `scripts/validate-schema-docs.mjs`
checks it against both SQL and Java migration source.

## SQL DDL and seed migrations

- [V1 schema metadata](../../backend/src/main/resources/db/migration/V1__schema_metadata.sql)
- [V2 identity](../../backend/src/main/resources/db/migration/V2__identity.sql)
- [V3 currency and products](../../backend/src/main/resources/db/migration/V3__currency_and_product_reference.sql)
- [V4 reference versions](../../backend/src/main/resources/db/migration/V4__pricing_reference_versions.sql)
- [V5 exchange rates](../../backend/src/main/resources/db/migration/V5__exchange_rates.sql)
- [V6 fixture records](../../backend/src/main/resources/db/migration/V6__runtime_fixture_records.sql)
- [V7 base-rate seed](../../backend/src/main/resources/db/migration/V7__seed_base_rate_reference.sql)
- [V8 receivables and quotes](../../backend/src/main/resources/db/migration/V8__assignors_receivables_and_pricing_quotes.sql)
- [V9 atomic settlements](../../backend/src/main/resources/db/migration/V9__atomic_settlements.sql)
- [V10 reversal, audit, and statement dimensions](../../backend/src/main/resources/db/migration/V10__settlement_reversals_audit_and_statement_ledger.sql)
- [V11 quote snapshot constraints](../../backend/src/main/resources/db/migration/V11__protect_financial_snapshots.sql)
- [V17 bounded monthly reference rates](../../backend/src/main/resources/db/migration/V17__bound_monthly_reference_rates.sql)
- [V18 reference-rate authorship](../../backend/src/main/resources/db/migration/V18__audit_reference_rate_versions.sql)
- [V21 statement filter indexes](../../backend/src/main/resources/db/migration/V21__index_statement_filter_branches.sql)

## Java Flyway migrations

- [V12 quote/Settlement immutability](../../backend/src/main/java/db/migration/V12__enforce_financial_snapshot_immutability.java)
- [V13 audit immutability](../../backend/src/main/java/db/migration/V13__enforce_audit_event_immutability.java)
- [V14 exchange-rate immutability](../../backend/src/main/java/db/migration/V14__protect_exchange_rate_history.java)
- [V15 complete quote snapshot](../../backend/src/main/java/db/migration/V15__complete_pricing_quote_snapshots.java)
- [V16 reversal immutability](../../backend/src/main/java/db/migration/V16__protect_settlement_reversal_history.java)
- [V19 reference-rate immutability](../../backend/src/main/java/db/migration/V19__protect_reference_rate_history.java)
- [V20 maximum receivable and quote terms](../../backend/src/main/java/db/migration/V20__bound_receivable_maturity.java)

V15 adds `pricing_quotes.product_type_code` and its foreign key. V12–V16 and
V19 use PostgreSQL triggers because H2 cannot execute PL/pgSQL; PostgreSQL
integration tests are therefore required evidence for append-only behavior.

## Named constraints, indexes, and triggers

- Constraints: `idempotency_records_state_check`, `settlement_items_asset_currency_fk`, `settlement_items_product_type_fk`, `pricing_quotes_face_currency_fk`, `pricing_quotes_fx_base_currency_fk`, `pricing_quotes_fx_quote_currency_fk`, `pricing_quotes_face_amount_positive`, `pricing_quotes_discounted_amount_positive`, `pricing_quotes_fx_rate_positive`, `pricing_quotes_settlement_amount_positive`, `pricing_quotes_expiry_after_pricing`, `pricing_quotes_product_type_fk`, `base_rate_versions_monthly_rate_domain`, `product_spread_versions_monthly_spread_domain`, `receivables_maximum_maturity`, `pricing_quotes_maximum_term`.
- Indexes: `exchange_rates_pair_observed_at_idx`, `pricing_quotes_receivable_id_idx`, `audit_events_target_idx`, `settlement_reversals_settlement_idx`, `settlements_created_at_idx`, `settlement_items_statement_dimensions_idx`, `settlements_statement_filter_idx`, `settlement_reversals_statement_filter_idx`, `settlement_items_product_statement_idx`.
- PostgreSQL triggers: `pricing_quotes_immutable`, `settlements_immutable`, `settlement_items_immutable`, `audit_events_immutable`, `exchange_rates_immutable`, `settlement_reversals_immutable`, `base_rate_versions_immutable`, `product_spread_versions_immutable`.

## Structural constraint signatures

Primary keys: `schema_metadata.primary(id)`, `users.primary(id)`,
`user_roles.primary(user_id,role)`, `currencies.primary(code)`,
`product_types.primary(code)`, `base_rate_versions.primary(id)`,
`product_spread_versions.primary(id)`, `exchange_rates.primary(id)`,
`runtime_fixture_records.primary(fixture_id)`, `assignors.primary(id)`,
`receivables.primary(id)`, `pricing_quotes.primary(id)`,
`settlements.primary(id)`, `settlement_items.primary(id)`,
`idempotency_records.primary(id)`, `settlement_reversals.primary(id)`, and
`audit_events.primary(id)`.

Uniqueness: `schema_metadata.unique(schema_name)`, `users.unique(email)`,
`base_rate_versions.unique(currency_code,effective_at)`,
`product_spread_versions.unique(product_type_code,effective_at)`,
`exchange_rates.unique(base_currency_code,quote_currency_code,source,observed_at)`,
`assignors.unique(normalized_tax_id)`, `settlement_items.unique(quote_id)`,
`settlement_items.unique(receivable_id)`,
`settlement_items.unique(settlement_id,item_position)`,
`idempotency_records.unique(actor,operation,idempotency_key)`, and
`settlement_reversals.unique(settlement_id)`.

Foreign keys: `user_roles.fk(user_id->users.id)`,
`base_rate_versions.fk(currency_code->currencies.code)`,
`product_spread_versions.fk(product_type_code->product_types.code)`,
`exchange_rates.fk(base_currency_code->currencies.code)`,
`exchange_rates.fk(quote_currency_code->currencies.code)`,
`receivables.fk(assignor_id->assignors.id)`,
`receivables.fk(product_type_code->product_types.code)`,
`receivables.fk(face_currency_code->currencies.code)`,
`pricing_quotes.fk(receivable_id->receivables.id)`,
`pricing_quotes.fk(settlement_currency_code->currencies.code)`,
`settlements.fk(assignor_id->assignors.id)`,
`settlements.fk(settlement_currency_code->currencies.code)`,
`settlement_items.fk(settlement_id->settlements.id)`,
`settlement_items.fk(quote_id->pricing_quotes.id)`,
`settlement_items.fk(receivable_id->receivables.id)`,
`idempotency_records.fk(settlement_id->settlements.id)`, and
`settlement_reversals.fk(settlement_id->settlements.id)`.

Foreign keys added after initial table creation:
`idempotency_records.fk(reversal_id->settlement_reversals.id)`,
`settlement_items.fk(asset_currency_code->currencies.code)`,
`settlement_items.fk(product_type_code->product_types.code)`,
`pricing_quotes.fk(face_currency_code->currencies.code)`,
`pricing_quotes.fk(fx_base_currency_code->currencies.code)`,
`pricing_quotes.fk(fx_quote_currency_code->currencies.code)`, and
`pricing_quotes.fk(product_type_code->product_types.code)`.

Inline checks: `base_rate_versions.check(monthly_rate)`,
`product_spread_versions.check(monthly_spread)`, `exchange_rates.check(rate)`,
`exchange_rates.check(table)`, `receivables.check(face_amount)`,
`receivables.check(due_date)`, `receivables.check(status)`,
`settlements.check(total_amount)`, `settlements.check(status)`,
`settlement_items.check(item_position)`,
`settlement_items.check(settlement_amount)`,
`idempotency_records.check(status)`, `idempotency_records.check(table)`,
`settlement_reversals.check(reason)`, and `pricing_quotes.check(status)`.

Checks added after initial table creation retain their normalized expressions so
changing an operator, bound, or state combination cannot pass by preserving only
the constraint name:

- `idempotency_records.check((status='processing'andsettlement_idisnullandreversal_idisnullandcompleted_atisnull)or(status='completed'and((settlement_idisnotnullandreversal_idisnull)or(settlement_idisnullandreversal_idisnotnull))andcompleted_atisnotnull))`
- `pricing_quotes.check(face_amount>0)`
- `pricing_quotes.check(discounted_amount>0)`
- `pricing_quotes.check(fx_rate>0)`
- `pricing_quotes.check(settlement_amount>0)`
- `pricing_quotes.check(expires_at>pricing_at)`
- `base_rate_versions.check(monthly_rate<=1.0000000000)`
- `product_spread_versions.check(monthly_spread<=1.0000000000)`
- `receivables.check(due_date<=[issue_date+interval'10years'|dateadd(year,10,issue_date)])`
- `pricing_quotes.check(due_date<=[cast(pricing_atasdate)+interval'10years'|dateadd(year,10,cast(pricing_atasdate))])`
