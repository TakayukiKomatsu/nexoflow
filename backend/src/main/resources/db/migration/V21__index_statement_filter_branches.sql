create index settlements_statement_filter_idx
    on settlements(assignor_id, settlement_currency_code, created_at desc, id desc);

create index settlement_reversals_statement_filter_idx
    on settlement_reversals(reversed_at desc, settlement_id, id desc);

create index settlement_items_product_statement_idx
    on settlement_items(product_type_code, settlement_id, receivable_id);
