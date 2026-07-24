# Task 3 — currency and pricing adapter extraction

- Currency policy, application ports, and JDBC adapters added; production `JdbcCurrencyService` and `JdbcReferenceRateService` removed.
- Pricing quote snapshot, ports, and JDBC adapters added; `AuthoritativePricingService` no longer imports JDBC or SQL types.
- Passed: `*PricingExactVectorTest`, `*AuthoritativePricingServiceTest`, `*FxConversionPolicyTest`, `*PricingQuoteSnapshotTest`; `make test-api-features`.
- Global financial layering test now fails only pending the settlement extraction (four aggregate guards), as expected before Task 4.
