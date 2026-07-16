.PHONY: test-hooks test-unit install-hooks

test-hooks:
	./scripts/tests/test_commit_message_hook.sh
	./scripts/tests/test_pre_commit_secret_hook.sh
	./scripts/tests/test_hook_installation.sh

test-unit:
	./scripts/with-java21.sh ./backend/gradlew -p backend test
	npm --prefix frontend run test -- --run

install-hooks:
	./scripts/install-git-hooks.sh
