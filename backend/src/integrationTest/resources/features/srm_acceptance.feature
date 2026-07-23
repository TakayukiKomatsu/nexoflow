Feature: SRM executable acceptance

  Scenario: AUTH-003 complete endpoint role matrix
    Given an anonymous POST to "/api/v1/receivables" with empty JSON body
    Then the response status is 401
    And the response problem code is "AUTHENTICATION_REQUIRED"
    Given an anonymous GET to "/api/v1/audit-events"
    Then the response status is 401
    Given the OPERATOR user is authenticated
    When the OPERATOR posts to "/api/v1/exchange-rates" with body "{\"base\":\"USD\",\"quote\":\"BRL\",\"rate\":5.2,\"source\":\"auth\",\"observedAt\":\"2030-01-15T12:00:00Z\"}"
    Then the response status is 403
    And the response problem code is "ACCESS_DENIED"
    When the OPERATOR posts to "/api/v1/settlements/00000000-0000-0000-0000-ffffffffffff/reversals" with body "{\"reason\":\"auth-test\"}"
    Then the response status is 403
    Given the ADMIN user is authenticated
    When the ADMIN gets "/api/v1/exchange-rates?base=USD&quote=BRL"
    Then the response status is 200
    When the ADMIN posts to "/api/v1/exchange-rates" with body "{\"base\":\"USD\",\"quote\":\"BRL\",\"rate\":\"5.2000000000\",\"source\":\"auth-matrix\",\"observedAt\":\"2030-01-10T00:00:00Z\"}"
    Then the response status is 201
    When the OPERATOR gets "/api/v1/settlement-statements"
    Then the response status is 200
    And the protected endpoint role matrix is enforced for every actor role

  Scenario: FX-003 direct inverse identity and exact freshness boundary
    Given the ADMIN user is authenticated
    And the OPERATOR user is authenticated
    When the ADMIN records exchange rate "USD/BRL" as "5.2000000000" at "2030-01-15T12:00:00Z" from "fx003-source"
    Then the response status is 201
    When the OPERATOR requests conversion of "100" "USD" to "BRL" at "2030-01-15T12:00:00Z"
    Then the response status is 200
    And the conversion settlement amount is "520.00"
    When the OPERATOR requests conversion of "520" "BRL" to "USD" at "2030-01-15T12:00:00Z"
    Then the response status is 200
    And the conversion settlement amount is "100.00"
    When the OPERATOR requests conversion of "100" "USD" to "BRL" at "2030-01-16T12:00:00Z"
    Then the response status is 200
    And the conversion settlement amount is "520.00"
    When the OPERATOR requests conversion of "100" "USD" to "BRL" at "2030-01-16T12:00:01Z"
    Then the response status is 422
    And the response problem code is "FX_RATE_STALE"

  Scenario: FX-004 latest non-stale direct rate is selected
    Given the ADMIN user is authenticated
    And the OPERATOR user is authenticated
    When the ADMIN records exchange rate "USD/BRL" as "5.1000000000" at "2030-01-14T11:59:59Z" from "fx004-stale"
    Then the response status is 201
    When the ADMIN records exchange rate "USD/BRL" as "5.2000000000" at "2030-01-15T11:00:00Z" from "fx004-current"
    Then the response status is 201
    When the OPERATOR requests conversion of "100" "USD" to "BRL" at "2030-01-15T12:00:00Z"
    Then the response status is 200
    And the conversion settlement amount is "520.00"

  Scenario: FX-RES-006 transient provider failures are bounded
    Given the ADMIN user is authenticated
    And the FX provider will return transient statuses "500,429,502"
    When the ADMIN triggers FX synchronization for "USD" to "BRL"
    Then the response status is 503
    And the response problem code is "FX_PROVIDER_UNAVAILABLE"
    And the FX provider request count is 3
    When the ADMIN triggers FX synchronization for "USD" to "BRL"
    Then the response status is 503
    And the FX provider request count is 3
    Given the FX provider will return rate "5.2000000000" observed at "2030-01-15T12:00:00Z"
    When the FX circuit-open interval elapses
    And the ADMIN triggers FX synchronization for "USD" to "BRL"
    Then the response status is 201
    And the FX provider request count is 4
    Given the FX provider will return permanent status 400
    When the ADMIN triggers FX synchronization for "BRL" to "USD"
    Then the response status is 503
    And the response problem code is "FX_PROVIDER_UNAVAILABLE"
    And the FX provider request count is 5

  Scenario: PRICE-001 server simulation is exact and does not create a quote
    Given the OPERATOR user is authenticated
    And the pricing quote row count is recorded
    When the OPERATOR simulates pricing with face amount "1000.00" "BRL" product "MERCANTILE_INVOICE" due "2030-02-14" settlement "BRL"
    Then the response status is 200
    And the simulation discounted amount is "975.6098"
    And the simulation settlement amount is "975.61"
    And the pricing quote row count is unchanged

  Scenario: PRICE-002 independent cheque cross-currency vector
    Given the OPERATOR user is authenticated
    When the OPERATOR simulates pricing with face amount "1000.00" "BRL" product "POST_DATED_CHEQUE" due "2030-02-14" settlement "USD"
    Then the response status is 200
    And the simulation discounted amount is "966.1836"
    And the simulation settlement amount is "185.80"

  Scenario: QUOTE-005 immutable exact quote roundtrip
    Given the OPERATOR user is authenticated
    When the OPERATOR creates a receivable with amount "1000.00" currency "BRL" product "MERCANTILE_INVOICE" issue "2030-01-01" due "2030-02-14"
    Then the response status is 201
    When the OPERATOR creates a pricing quote for the last receivable with settlement currency "BRL"
    Then the response status is 201
    And the quote status is "ACTIVE"
    And the quote discounted amount is "975.6098"
    And the quote settlement amount is "975.61"
    When the OPERATOR retrieves the last pricing quote
    Then the response status is 200
    And the quote status is "ACTIVE"
    And the quote settlement amount is "975.61"
    And the quote face amount is "1000.0000"
    And the database rejects mutations of the last pricing quote snapshot

  Scenario: SETTLE-006 same-key concurrency returns one exact settlement
    Given the OPERATOR user is authenticated
    When the OPERATOR creates a receivable with amount "1000.00" currency "BRL" product "MERCANTILE_INVOICE" issue "2030-01-01" due "2030-02-14"
    Then the response status is 201
    And the OPERATOR creates a second receivable with amount "1000.00" currency "BRL" product "POST_DATED_CHEQUE" issue "2030-01-01" due "2030-02-14"
    When the OPERATOR creates a pricing quote for the first receivable with settlement currency "BRL"
    Then the response status is 201
    And the OPERATOR creates a pricing quote for the second receivable with settlement currency "BRL"
    Then the response status is 201
    And a same-key idempotency claim barrier is installed
    When two concurrent OPERATOR requests settle both quotes with idempotency key "cucumber-claim-settle-006"
    Then both concurrent responses are byte-identical with exactly one replay
    And the database contains one completed settlement with ordered items and one audit event

  Scenario: SETTLE-ROLLBACK-008 fault rollback leaves no financial rows
    Given the OPERATOR user is authenticated
    When the OPERATOR creates a receivable with amount "1000.00" currency "BRL" product "MERCANTILE_INVOICE" issue "2030-01-01" due "2030-02-14"
    Then the response status is 201
    And the OPERATOR creates a second receivable with amount "1000.00" currency "BRL" product "POST_DATED_CHEQUE" issue "2030-01-01" due "2030-02-14"
    When the OPERATOR creates a pricing quote for the first receivable with settlement currency "BRL"
    Then the response status is 201
    And the OPERATOR creates a pricing quote for the second receivable with settlement currency "BRL"
    Then the response status is 201
    Given the settlement idempotency key is "rollback-008-injected-key"
    And a database fault is armed after the first settlement item
    Then the scoped financial row counts are recorded for the two quotes
    When the OPERATOR settles both quotes with idempotency key "rollback-008-injected-key"
    Then the response status is 500
    And the response problem code is "INTERNAL_ERROR"
    And the database has no new financial or idempotency rows for the two quotes
    And both receivables are still "REGISTERED"
    And both quotes are still "ACTIVE"

  Scenario: REVERSE-007 whole reversal is terminal and idempotent
    Given the OPERATOR user is authenticated
    And the ADMIN user is authenticated
    When the OPERATOR creates a receivable with amount "1000.00" currency "BRL" product "MERCANTILE_INVOICE" issue "2030-01-01" due "2030-02-14"
    Then the response status is 201
    When the OPERATOR creates a pricing quote for the last receivable with settlement currency "BRL"
    Then the response status is 201
    When the OPERATOR settles the last quote with idempotency key "reverse-007-settle-key"
    Then the response status is 201
    When the ADMIN reverses the last settlement with reason "duplicate source document" and key "reverse-007-reverse-key"
    Then the response status is 201
    And the reversal id is captured
    When the ADMIN reverses the last settlement with reason "duplicate source document" and key "reverse-007-reverse-key"
    Then the response status is 201
    And the response header "Idempotent-Replay" is "true"
    And the reversal id matches the previous reversal
    When the ADMIN reverses the last settlement with reason "changed payload" and key "reverse-007-reverse-key"
    Then the response status is 409
    And the response problem code is "IDEMPOTENCY_KEY_REUSED"
    When the ADMIN reverses the last settlement with reason "different reason" and key "reverse-007-different-key"
    Then the response status is 409
    And the response problem code is "ALREADY_REVERSED"
    And the database has exactly 1 reversal for the last settlement
    And the receivable status is "REVERSED"
    And the database has one negative reversal ledger entry for each settlement item

  Scenario: REPORT-REV-003 signed ledger filters and stable pagination
    Given the OPERATOR user is authenticated
    And the ADMIN user is authenticated
    When the OPERATOR creates a receivable with amount "1000.00" currency "BRL" product "MERCANTILE_INVOICE" issue "2030-01-01" due "2030-02-14"
    Then the response status is 201
    And the OPERATOR creates a second receivable with amount "1000.00" currency "BRL" product "MERCANTILE_INVOICE" issue "2030-01-01" due "2030-02-14"
    When the OPERATOR creates a pricing quote for the first receivable with settlement currency "BRL"
    Then the response status is 201
    And the OPERATOR creates a pricing quote for the second receivable with settlement currency "BRL"
    Then the response status is 201
    When the OPERATOR settles both quotes with idempotency key "report-rev-003-settle"
    Then the response status is 201
    When the ADMIN reverses the last settlement with reason "report reversal test" and key "report-rev-003-reverse"
    Then the response status is 201
    When the OPERATOR queries the settlement statement with every supported filter
    Then the response status is 200
    And the statement has at least 4 entries for the last settlement assignor
    And every statement entry matches all requested filters
    And the statement settlement entries have positive signed amounts
    And the statement reversal entries have negative signed amounts
    And the statement entries are ordered by effective time and entry id descending
    When the OPERATOR queries the statement with page "0" size "2"
    Then the response status is 200
    And the page has 2 entries
    And has next page is "true"
    And the statement entries are ordered by effective time and entry id descending
    When the OPERATOR queries the statement with page "1" size "2"
    Then the response status is 200
    And the page has 2 entries
    And has next page is "false"
    And the second statement page does not overlap the first
  Scenario: OBS-003 settlement conflict is observable without leaking credentials
    Given the ADMIN user is authenticated
    And the OPERATOR user is authenticated
    When the OPERATOR creates a receivable with amount "1000.00" currency "BRL" product "MERCANTILE_INVOICE" issue "2030-01-01" due "2030-02-14"
    And the OPERATOR creates a pricing quote for the last receivable with settlement currency "BRL"
    And the OPERATOR settles the last quote with idempotency key "obs-003-a"
    Then the response status is 201
    When the OPERATOR attempts the last quote with idempotency key "obs-003-b" and correlation "obs-003-b"
    Then the response status is 409
    And the response problem code is "ALREADY_SETTLED"
    And operational logs contain correlation "obs-003-b"
    And operational logs contain only bounded fields and no credentials
    When the ADMIN retrieves Prometheus metrics
    Then the response status is 200
    And the metrics contain "srm_settlement_outcomes_total" with labels currency "BRL" and result "CONFLICT"
    And the metrics do not contain raw user input labels
