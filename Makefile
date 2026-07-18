.PHONY: test-hooks test-unit test-runtime verify-unit build install-hooks verify-fast validate-workflows validate-architecture-docs smoke-compose fixtures-e2e verify-readiness-recovery verify-compose test-log-redaction smoke-financial-path inspect-observability verify license-check

verify-fast: test-hooks test-unit
	./scripts/tests/test_frontend_quality.sh
	./scripts/tests/test_architecture_docs.sh
	./scripts/tests/test_ci_workflow.sh

validate-workflows:
	./scripts/tests/test_ci_workflow.sh

validate-architecture-docs:
	./scripts/tests/test_architecture_docs.sh

test-hooks:
	./scripts/tests/test_commit_message_hook.sh
	./scripts/tests/test_pre_commit_secret_hook.sh
	./scripts/tests/test_hook_installation.sh

test-unit:
	./scripts/with-java21.sh ./backend/gradlew -p backend test
	npm --prefix frontend run test -- --run

test-runtime:
	@command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1 || { echo 'BLOCKED: test-runtime requires a working Docker daemon for Testcontainers.' >&2; exit 2; }
	./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*Runtime*' --tests '*ApiErrorContractTest' --tests '*MigrationSmokeTest'
	./scripts/with-java21.sh ./backend/gradlew -p backend integrationTest

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
	@trap 'docker compose down -v --remove-orphans' EXIT; \
		docker compose config >/dev/null; \
		docker compose up --build --wait; \
		$(MAKE) smoke-compose; \
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
