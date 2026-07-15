.PHONY: test-hooks

test-hooks:
	./scripts/tests/test_commit_message_hook.sh
	./scripts/tests/test_pre_commit_secret_hook.sh
