# AI usage record

## Implemented record

Codex was used as an engineering copilot for repository exploration, SDD review, test-first fixes, documentation maintenance, and verification. The human owner remains responsible for the delivered design and code.

## Strategic prompts and outcomes

- “Read the docs available on docs and check our SDD plan and implementation.” This produced a gap review against the source requirements and the 12-prompt SDD suite.
- “Sure, lets fix everything, and check the code base as well.” This drove test-first repairs to the pre-push hook, financial value invariants, JWT verification, authorization errors, login rate limiting, CI/doc validation, and traceability.

## Corrections to AI-generated or incomplete work

- A prior hook installation test checked only executability; the actual pre-push hook referenced a missing Make target. The acceptance test now verifies the target exists and the target is implemented.
- A manually assembled JWT could issue a token but could not authenticate requests. It was replaced with Spring Security JWT encoding/decoding and an explicit role-claim converter.
- The initial default JWT secret and H2 runtime configuration could be mistaken for production defaults. Runtime configuration now requires environment values, while H2 exists only in test resources.
- Initial architecture and CI checks were string-presence checks. They now validate local documentation links, migration-to-ER coverage, action SHA pinning, build execution, and security-review jobs.

## Trade-offs and limitations

AI accelerated small, reviewable slices and helped surface mismatches between documentation and code. It did not provide PostgreSQL/Testcontainers, Docker Compose, real FX-provider, settlement, reporting, E2E, or release evidence; those remain explicitly tracked SDD work rather than inferred completion.
