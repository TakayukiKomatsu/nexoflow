create table exchange_rates (
    id uuid primary key,
    base_currency_code varchar(3) not null references currencies(code),
    quote_currency_code varchar(3) not null references currencies(code),
    rate numeric(19,10) not null check (rate > 0),
    source varchar(50) not null,
    observed_at timestamp not null,
    created_at timestamp not null,
    created_by varchar(320) not null,
    check (base_currency_code <> quote_currency_code),
    unique (base_currency_code, quote_currency_code, source, observed_at)
);
create index exchange_rates_pair_observed_at_idx on exchange_rates (base_currency_code, quote_currency_code, observed_at desc);
