# Task 3 Report

## Commit

`f79be6d` — `test: validate frontend quote OpenAPI contract`

## Paths

- `backend/src/main/java/com/srm/creditengine/pricing/api/PricingController.java`
- `backend/src/test/java/com/srm/creditengine/api/RuntimeMetadataContractTest.java`
- `frontend/scripts/validate-pricing-quote-contract.mjs`
- `frontend/package.json`
- `Makefile`

## Scope correction

The generated OpenAPI document gave both the receivable response and the nested pricing response the generic `Response` component name. Task 3 therefore adds `@Schema(name = "PricingBreakdownResponse")` to the nested pricing record, without changing public JSON fields or endpoint behavior. The runtime assertion requires that unique reference and verifies its pricing-only field set.

## Verification

`./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*RuntimeMetadataContractTest'`

```text
BUILD SUCCESSFUL in 5s
5 actionable tasks: 3 executed, 2 up-to-date
```

`make validate-frontend-api-contract`

```text
BUILD SUCCESSFUL in 349ms
5 actionable tasks: 5 up-to-date
API-CONTRACT-001 passed: frontend quote shape matches OpenAPI quote boundary
```
