.PHONY: test-hooks test-unit verify-unit build install-hooks verify-fast validate-workflows validate-architecture-docs

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

verify-unit: test-unit

build:
	./scripts/with-java21.sh ./backend/gradlew -p backend build
	npm --prefix frontend run build

install-hooks:
	./scripts/install-git-hooks.sh
