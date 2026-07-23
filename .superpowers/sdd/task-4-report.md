# Task 4 — settlement domain/application/infrastructure extraction

- Settlement orchestration now lives in `SettlementApplicationService`; it keeps the transaction boundary and coordinates pure policy plus typed persistence, idempotency, and audit ports.
- `SettlementPolicy` owns validation, preview aggregation, reversal validation, and deterministic hashes. JDBC locking, consumption, settlement/reversal writes, result hydration, idempotency, and audit recording are isolated in infrastructure adapters.
- Preserved the original write ordering: quote/receivable consumption occurs before the corresponding settlement-item insert, so the injected duplicate-receivable failure remains a mid-transaction rollback.
- Added policy, JDBC adapter, and branch-coverage tests. Updated currency fixture typing so the full coverage run exercises domain FX observations rather than stale service DTOs.
- Passed: `make test-runtime`; `make test-api-features`; financial module architecture tests; `make test-coverage`.
