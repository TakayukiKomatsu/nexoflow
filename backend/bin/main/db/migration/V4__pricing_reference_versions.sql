create table base_rate_versions (id uuid primary key, currency_code varchar(3) not null references currencies(code), monthly_rate numeric(19,10) not null check (monthly_rate > 0), effective_at timestamp not null, unique (currency_code, effective_at));
create table product_spread_versions (id uuid primary key, product_type_code varchar(50) not null references product_types(code), monthly_spread numeric(19,10) not null check (monthly_spread > 0), effective_at timestamp not null, unique (product_type_code, effective_at));
insert into product_spread_versions (id, product_type_code, monthly_spread, effective_at) values
('00000000-0000-0000-0000-000000000101', 'MERCANTILE_INVOICE', 0.0150000000, timestamp '2030-01-01 00:00:00'),
('00000000-0000-0000-0000-000000000102', 'POST_DATED_CHEQUE', 0.0250000000, timestamp '2030-01-01 00:00:00');
