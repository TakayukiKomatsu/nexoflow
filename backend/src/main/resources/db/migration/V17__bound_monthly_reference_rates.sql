alter table base_rate_versions
    add constraint base_rate_versions_monthly_rate_domain
    check (monthly_rate <= 1.0000000000);

alter table product_spread_versions
    add constraint product_spread_versions_monthly_spread_domain
    check (monthly_spread <= 1.0000000000);
