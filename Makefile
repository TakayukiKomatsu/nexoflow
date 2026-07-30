.PHONY: test-java-wrapper test-hooks test-security-scan-script test-container-build-inputs test-local-collaboration-evidence test-crisis-evidence-contract test-reporting-evidence-contract test-unit test-runtime test-coverage verify-unit build install-hooks verify-fast validate-workflows validate-architecture-docs smoke-compose fixtures-e2e verify-readiness-recovery verify-compose test-log-redaction smoke-financial-path inspect-observability verify license-check explain-statements-representative test-api-features test-ui-features e2e-fixed security-scan validate-docs validate-frontend-authority validate-frontend-api-contract test-frontend-api-contract-clean validate-traceability test-crisis-evidence release-check

verify-fast: test-java-wrapper test-hooks test-security-scan-script test-container-build-inputs test-local-collaboration-evidence test-crisis-evidence-contract test-reporting-evidence-contract test-frontend-api-contract-clean test-unit
	./scripts/tests/test_frontend_quality.sh
	./scripts/tests/test_architecture_docs.sh
	./scripts/tests/test_ci_workflow.sh

validate-workflows:
	./scripts/tests/test_ci_workflow.sh

validate-architecture-docs:
	./scripts/tests/test_architecture_docs.sh

test-java-wrapper:
	./scripts/tests/test_java_wrapper.sh

test-hooks:
	./scripts/tests/test_commit_message_hook.sh
	./scripts/tests/test_pre_commit_secret_hook.sh
	./scripts/tests/test_hook_installation.sh

test-security-scan-script:
	./scripts/tests/test_security_scan.sh

test-container-build-inputs:
	./scripts/tests/test_container_build_inputs.sh

test-local-collaboration-evidence:
	./scripts/tests/test_local_collaboration_evidence_contract.sh

test-crisis-evidence-contract:
	./scripts/tests/test_crisis_evidence_contract.sh

test-reporting-evidence-contract:
	./scripts/tests/test_reporting_evidence_contract.sh

test-unit:
	./scripts/with-java21.sh ./backend/gradlew -p backend test
	npm --prefix frontend run test -- --run

test-runtime:
	@command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1 || { echo 'BLOCKED: test-runtime requires a working Docker daemon for Testcontainers.' >&2; exit 2; }
	./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*Runtime*' --tests '*ApiErrorContractTest' --tests '*MigrationSmokeTest'
	./scripts/with-java21.sh ./backend/gradlew -p backend integrationTest

test-coverage:
	@command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1 || { echo 'BLOCKED: test-coverage requires a working Docker daemon for integration coverage.' >&2; exit 2; }
	./scripts/with-java21.sh ./backend/gradlew -p backend riskCoverage
	npm --prefix frontend run test:coverage

smoke-compose:
	docker compose --profile smoke run --rm smoke

smoke-financial-path:
	docker compose --profile smoke run --rm smoke

inspect-observability:
	./scripts/inspect-observability.sh

test-log-redaction:
	./scripts/tests/test_log_redaction.sh

verify: verify-fast test-log-redaction

fixtures-e2e:
	./scripts/verify-e2e-fixtures.sh

verify-readiness-recovery:
	./scripts/verify-readiness-recovery.sh

verify-compose:
	@command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1 || { echo 'BLOCKED: verify-compose requires a working Docker daemon.' >&2; exit 2; }
	@set -e; trap 'docker compose down -v --remove-orphans' EXIT; \
		docker compose config >/dev/null; \
		docker compose up --build --wait; \
		$(MAKE) smoke-compose; \
		$(MAKE) inspect-observability; \
		$(MAKE) fixtures-e2e; \
		$(MAKE) verify-readiness-recovery

verify-unit: test-unit

build:
	./scripts/with-java21.sh ./backend/gradlew -p backend build
	npm --prefix frontend run build

install-hooks:
	./scripts/install-git-hooks.sh

license-check:
	./scripts/with-java21.sh ./backend/gradlew -p backend checkLicense
	npm --prefix frontend run license:check

explain-statements-representative:
	@command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1 || { echo 'BLOCKED: explain-statements-representative requires a running Docker daemon.' >&2; exit 2; }
	./scripts/explain-statements-representative.sh

test-api-features:
	./scripts/with-java21.sh ./backend/gradlew -p backend integrationTest --tests '*RunCucumberTest'

test-ui-features:
	npm --prefix frontend run test:e2e

e2e-fixed: test-api-features test-ui-features

security-scan:
	./scripts/security-scan.sh

validate-docs:
	./scripts/validate-docs.sh


validate-frontend-authority:
	npm --prefix frontend run validate:authoritative-pricing
validate-frontend-api-contract:
	./scripts/with-java21.sh ./backend/gradlew -p backend exportOpenApi
	npm --prefix frontend run validate:pricing-quote-contract

test-frontend-api-contract-clean:
	./scripts/tests/test_frontend_api_contract_clean.sh

validate-traceability:
	./scripts/validate-traceability.sh
	./scripts/tests/test_traceability_validation.sh

test-crisis-evidence:
	./scripts/test-crisis-evidence.sh

release-check: verify-fast test-log-redaction build test-runtime test-coverage verify-compose e2e-fixed explain-statements-representative security-scan validate-docs validate-frontend-authority validate-frontend-api-contract validate-traceability test-crisis-evidence
