alter table pricing_quotes add constraint pricing_quotes_face_currency_fk foreign key (face_currency_code) references currencies(code);
alter table pricing_quotes add constraint pricing_quotes_fx_base_currency_fk foreign key (fx_base_currency_code) references currencies(code);
alter table pricing_quotes add constraint pricing_quotes_fx_quote_currency_fk foreign key (fx_quote_currency_code) references currencies(code);
alter table pricing_quotes add constraint pricing_quotes_face_amount_positive check (face_amount > 0);
alter table pricing_quotes add constraint pricing_quotes_discounted_amount_positive check (discounted_amount > 0);
alter table pricing_quotes add constraint pricing_quotes_fx_rate_positive check (fx_rate > 0);
alter table pricing_quotes add constraint pricing_quotes_settlement_amount_positive check (settlement_amount > 0);
alter table pricing_quotes add constraint pricing_quotes_expiry_after_pricing check (expires_at > pricing_at);

-- PostgreSQL-only immutability triggers are installed by the following Java Flyway
-- migration. Keeping the structural constraints here makes the H2 verification
-- profile exercise the same data model.
