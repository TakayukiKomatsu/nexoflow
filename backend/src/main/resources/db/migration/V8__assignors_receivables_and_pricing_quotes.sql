create table assignors (
    id uuid primary key,
    legal_name varchar(200) not null,
    normalized_tax_id varchar(32) not null unique,
    active boolean not null,
    created_at timestamp not null,
    created_by varchar(200) not null
);

create table receivables (
    id uuid primary key,
    assignor_id uuid not null references assignors(id),
    product_type_code varchar(50) not null references product_types(code),
    face_currency_code varchar(3) not null references currencies(code),
    face_amount numeric(19,4) not null check (face_amount > 0),
    issue_date date not null,
    due_date date not null check (due_date > issue_date),
    status varchar(20) not null check (status in ('REGISTERED', 'SETTLED', 'REVERSED')),
    version bigint not null default 0,
    created_at timestamp not null,
    created_by varchar(200) not null
);

create table pricing_quotes (
    id uuid primary key,
    receivable_id uuid not null references receivables(id),
    settlement_currency_code varchar(3) not null references currencies(code),
    face_amount numeric(19,4) not null,
    face_currency_code varchar(3) not null,
    due_date date not null,
    pricing_at timestamp not null,
    expires_at timestamp not null,
    base_rate numeric(19,10) not null,
    spread numeric(19,10) not null,
    strategy_code varchar(50) not null,
    day_count_convention varchar(50) not null,
    term_in_months numeric(19,10) not null,
    discounted_amount numeric(19,4) not null,
    fx_base_currency_code varchar(3) not null,
    fx_quote_currency_code varchar(3) not null,
    fx_rate numeric(19,10) not null,
    fx_source varchar(100) not null,
    fx_observed_at timestamp not null,
    settlement_amount numeric(19,4) not null,
    created_by varchar(200) not null
);
create index pricing_quotes_receivable_id_idx on pricing_quotes(receivable_id);
