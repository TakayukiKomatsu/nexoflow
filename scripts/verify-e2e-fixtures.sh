#!/usr/bin/env bash
set -euo pipefail

compose=(docker compose)
psql=("${compose[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U srm -d srm_credit_engine -At)

checksum() {
  "${psql[@]}" -c "
    select md5(string_agg(record, '|' order by record))
    from (
      select 'fixture:' || fixture_id || ':' || fixture_set || ':' || fixture_value || ':' || loaded_at::text as record
      from runtime_fixture_records
      where fixture_set = 'baseline-v1'
      union all
      select 'rate:' || id::text || ':' || base_currency_code || ':' || quote_currency_code || ':' || rate::text || ':' || source || ':' || observed_at::text || ':' || created_at::text as record
      from exchange_rates
      where id = '00000000-0000-0000-0000-000000000202'
    ) fixtures"
}

assert_expected_records_once() {
  local unexpected
  unexpected="$("${psql[@]}" -c "
    select count(*)
    from (
      select expected.fixture_id
      from (values ('e2e-clock'), ('e2e-usd-brl-rate'), ('e2e-assignor-id')) as expected(fixture_id)
      left join runtime_fixture_records actual on actual.fixture_id = expected.fixture_id
      group by expected.fixture_id
      having count(actual.fixture_id) <> 1
      union all
      select 'e2e-rate'
      where (select count(*) from exchange_rates where id = '00000000-0000-0000-0000-000000000202') <> 1
    ) invalid")"
  [[ "$unexpected" == "0" ]] || { echo "OPS-FIX-002 failed: fixed fixture IDs must occur exactly once" >&2; exit 1; }
}

"${compose[@]}" --profile fixtures run --rm e2e-fixtures
assert_expected_records_once
first_checksum="$(checksum)"

"${compose[@]}" --profile fixtures run --rm e2e-fixtures
assert_expected_records_once
second_checksum="$(checksum)"

[[ -n "$first_checksum" && "$first_checksum" == "$second_checksum" ]] || {
  echo "OPS-FIX-002 failed: second fixture checksum differs from the first" >&2
  exit 1
}

echo "OPS-FIX-002 passed: baseline-v1 fixture IDs are unique and deterministic ($second_checksum)"
