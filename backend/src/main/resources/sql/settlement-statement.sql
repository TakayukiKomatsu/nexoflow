select entry_id,
       entry_type,
       signed_amount,
       effective_at,
       settlement_id,
       reversal_id,
       assignor_id,
       asset_currency_code,
       settlement_currency_code,
       product_type_code,
       receivable_id
from (
    select md5('SETTLEMENT:' || i.id::text)::uuid as entry_id,
           'SETTLEMENT' as entry_type,
           i.settlement_amount as signed_amount,
           s.created_at as effective_at,
           s.id as settlement_id,
           null::uuid as reversal_id,
           s.assignor_id,
           i.asset_currency_code,
           s.settlement_currency_code,
           i.product_type_code,
           i.receivable_id
    from settlements s
    join settlement_items i on i.settlement_id = s.id
    union all
    select md5('REVERSAL:' || r.id::text || ':' || i.id::text)::uuid as entry_id,
           'REVERSAL' as entry_type,
           -i.settlement_amount as signed_amount,
           r.reversed_at as effective_at,
           s.id as settlement_id,
           r.id as reversal_id,
           s.assignor_id,
           i.asset_currency_code,
           s.settlement_currency_code,
           i.product_type_code,
           i.receivable_id
    from settlement_reversals r
    join settlements s on s.id = r.settlement_id
    join settlement_items i on i.settlement_id = s.id
) ledger
where true
/*?from*/ and effective_at >= :from
/*?to*/ and effective_at < :to
/*?assignorId*/ and assignor_id = :assignorId
/*?assetCurrency*/ and asset_currency_code = :assetCurrency
/*?settlementCurrency*/ and settlement_currency_code = :settlementCurrency
/*?productType*/ and product_type_code = :productType
order by effective_at desc, entry_id desc
limit :limit offset :offset
