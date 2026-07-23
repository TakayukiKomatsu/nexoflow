alter table base_rate_versions add column created_by varchar(200);
update base_rate_versions set created_by = 'SYSTEM_MIGRATION' where created_by is null;
alter table base_rate_versions alter column created_by set not null;

alter table product_spread_versions add column created_by varchar(200);
update product_spread_versions set created_by = 'SYSTEM_MIGRATION' where created_by is null;
alter table product_spread_versions alter column created_by set not null;

-- Predecessor versions make system-clock native development usable before the
-- fixed 2030 acceptance versions become effective; the later policy remains intact.
insert into base_rate_versions
    (id, currency_code, monthly_rate, effective_at, created_by)
values
    ('00000000-0000-0000-0000-000000000104', 'BRL', 0.0100000000, timestamp '2026-01-01 00:00:00', 'SYSTEM_MIGRATION');

insert into product_spread_versions
    (id, product_type_code, monthly_spread, effective_at, created_by)
values
    ('00000000-0000-0000-0000-000000000105', 'MERCANTILE_INVOICE', 0.0150000000, timestamp '2026-01-01 00:00:00', 'SYSTEM_MIGRATION'),
    ('00000000-0000-0000-0000-000000000106', 'POST_DATED_CHEQUE', 0.0250000000, timestamp '2026-01-01 00:00:00', 'SYSTEM_MIGRATION');
