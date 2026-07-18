# SRM Frontend Workflows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent stale or mismatched financial state, preserve retry-safe Settlement intent, align role visibility, and make the complete operator path accessible and deterministic.

**Architecture:** Model upstream/downstream state explicitly and invalidate dependents when inputs change. Every request is abortable and guarded by a monotonically increasing request identity; controlled Problem Detail codes drive recovery rather than HTTP status alone.

**Tech Stack:** React 19, TypeScript 6, Fetch API, Vitest, Testing Library, jest-axe.

## Global Constraints

- The browser displays server-authoritative decimal strings and never calculates money.
- OPERATOR and ADMIN may mutate; OPERATOR, ANALYST, ADMIN, and AUDITOR may view the ledger.
- A request response may update state only when it matches the current input/selection/filter identity.
- Unknown Settlement outcome preserves the same idempotency key.
- Proven terminal/conflict outcomes clear or replace the intent according to the machine-readable error code.
- No arbitrary sleeps in tests; use deferred promises and fake timers.

---

### Task 1: Preserve Problem Details and support cancellation

**Files:**
- Modify: `frontend/src/api/client.ts`
- Test: `frontend/src/App.test.tsx`
- Test: `frontend/src/SettlementWorkspace.test.tsx`

**Interfaces:**
- Produces: `ProblemDetail` and `ApiError.status`, `.code`, `.correlationId`.
- Produces: optional `AbortSignal` on preview, settle, settlement-detail, and statement requests.

- [ ] **Step 1: Add failing client-facing tests**

Mock an RFC 9457 response containing `code: "IDEMPOTENCY_KEY_REUSED"` and `correlationId: "corr-1"`; assert the component receives both values. Assert aborting preview and statement fetches prevents state updates.

- [ ] **Step 2: Run frontend tests red**

```bash
npm --prefix frontend run test -- --run src/App.test.tsx src/SettlementWorkspace.test.tsx
```

- [ ] **Step 3: Extend the API error model**

Implement:

```ts
export type ProblemDetail = {
  status?: number;
  detail?: string;
  message?: string;
  code?: string;
  correlationId?: string;
  violations?: Array<{ field: string; message: string }>;
};

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
    message: string,
    readonly correlationId?: string,
  ) {
    super(message);
  }
}
```

In `request`, parse `ProblemDetail` and throw:

```ts
throw new ApiError(
  response.status,
  body?.code,
  body?.detail ?? body?.message ?? `Request failed (${response.status}).`,
  body?.correlationId,
);
```

- [ ] **Step 4: Add signals without duplicating clients**

Use signatures:

```ts
previewSettlement(quoteIds: string[], token: string, signal?: AbortSignal)
settle(quoteIds: string[], key: string, token: string, signal?: AbortSignal)
settlement(settlementId: string, token: string, signal?: AbortSignal)
statement(search: URLSearchParams, token: string, signal?: AbortSignal)
```

Pass `signal` into the existing `RequestInit` object.

- [ ] **Step 5: Verify typed errors and cancellation**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 6: Commit the API client contract**

```bash
git add -- frontend/src/api/client.ts frontend/src/App.test.tsx frontend/src/SettlementWorkspace.test.tsx
git commit -m "fix(frontend): preserve API error and abort metadata"
```

### Task 2: Make pricing workflow state dependency-safe

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.css`
- Test: `frontend/src/App.test.tsx`
- Test: `frontend/src/a11y.test.tsx`

**Interfaces:**
- Produces: form fingerprint containing assignor, product, face amount/currency, issue/due dates, and settlement currency.
- Produces: independent `receivablePending` and `quotePending` flags.

- [ ] **Step 1: Add failing state-integrity tests**

Use deferred request promises to prove:

```text
register Receivable A, change any one input -> receivableId cleared and Create quote disabled
successful simulation A, change to B, B fails -> A remains visibly marked stale/error and cannot be read as current
ANALYST or AUDITOR session -> no pricing-simulation request
second click while Receivable POST pending -> one request
second click while Quote POST pending -> one request
```

- [ ] **Step 2: Run pricing workflow tests red**

```bash
npm --prefix frontend run test -- --run src/App.test.tsx src/a11y.test.tsx
```

- [ ] **Step 3: Invalidate dependent state on every input change**

Implement one setter:

```ts
function set<K extends keyof FormValues>(key: K, value: FormValues[K]) {
  setValues((old) => ({ ...old, [key]: value }));
  setReceivableId(undefined);
  setMessage(undefined);
}
```

Quotes already created remain immutable history, but the current Receivable/Quote action chain is cleared. Do not retain a Receivable ID for a changed assignor, amount, currency, product, or date.

- [ ] **Step 4: Gate simulation by permission**

Before scheduling a simulation request:

```ts
if (!canCreate) {
  setSimulation(undefined);
  simulationRef.current = undefined;
  setState("idle");
  return;
}
```

Include `canCreate` in the effect dependency list.

- [ ] **Step 5: Preserve failed refresh as visibly stale**

Keep the last successful result but render it inside a container with `aria-label="Stale server simulation"`, `.stale-result`, and an alert/status message such as `Latest server refresh failed — displayed values are stale.` The `error` branch must never fall through to the generic current-authority text.

- [ ] **Step 6: Prevent duplicate mutations**

Set separate pending flags before each POST and clear in `finally`. Disable the corresponding button while pending and use labels `Registering…` and `Creating quote…`.

- [ ] **Step 7: Render complete Quote breakdowns**

For each quote display product type, due date, face amount/currency, base rate, spread, strategy, day count, term, discounted amount, FX pair/rate/source/observation, settlement amount/currency, priced-at, expiry, and status. Use a `<dl>` with one stable heading per Quote.

- [ ] **Step 8: Correct field-level accessibility**

Associate the amount error only with `faceAmount` through a stable error ID and `aria-describedby`; mark each invalid currency/date/assignor field independently. On failed mutation, focus the first invalid field or the alert summary. Keep keyboard order equal to visual workflow order.

- [ ] **Step 9: Verify workflow and accessibility**

```bash
npm --prefix frontend run test -- --run src/App.test.tsx src/a11y.test.tsx
npm --prefix frontend run typecheck
```

Expected: PASS.

- [ ] **Step 10: Commit pricing workflow safety**

```bash
git add -- frontend/src/App.tsx frontend/src/App.css frontend/src/App.test.tsx frontend/src/a11y.test.tsx
git commit -m "fix(frontend): invalidate stale pricing workflow state"
```

### Task 3: Sequence Preview and classify Settlement recovery

**Files:**
- Modify: `frontend/src/SettlementWorkspace.tsx`
- Test: `frontend/src/SettlementWorkspace.test.tsx`

**Interfaces:**
- Produces: `PreviewState = { quoteKey: string; value: SettlementPreview }`.
- Produces: `SETTLEMENT_TIMEOUT_MS = 10_000`.

- [ ] **Step 1: Add failing race/recovery tests**

Prove with deferred promises and fake timers:

```text
preview A resolves after selection B -> A ignored
preview B resolves first and A later -> B remains
preview reaches earliestExpiry -> Confirm disabled without a click
settle exceeds 10 seconds -> unknown outcome message and same stored key retained
IDEMPOTENCY_KEY_REUSED -> stored key removed, preview cleared, new preview required
ALREADY_SETTLED -> stored key removed, preview cleared, ledger guidance shown
PRICING_QUOTE_EXPIRED -> stored key removed, preview cleared, fresh quote guidance shown
network/5xx/timeout -> stored key retained for exact retry
```

- [ ] **Step 2: Run Settlement tests red**

```bash
npm --prefix frontend run test -- --run src/SettlementWorkspace.test.tsx
```

- [ ] **Step 3: Key Preview to ordered selection**

Derive:

```ts
const quoteKey = selected.join(",");
type PreviewState = { quoteKey: string; value: SettlementPreview };
```

Keep a `previewRequestId` ref. Abort the previous request before starting another. Install a response only when both request ID and captured `quoteKey` still match current state.

- [ ] **Step 4: Disable confirmation at expiry time**

When Preview installs, schedule one timeout for `max(0, earliestExpiry - Date.now())` that marks it expired. Clear the timeout on Preview/selection/unmount changes. Render Confirm disabled when pending, mismatched, or expired.

- [ ] **Step 5: Bound confirmation without losing intent**

Create an `AbortController`, schedule abort at `SETTLEMENT_TIMEOUT_MS`, and pass its signal to `api.settle`. On timeout/network/5xx, keep the persisted `Intent` and state that retry uses the same key. Clear the timeout in `finally`.

- [ ] **Step 6: Branch on machine code**

Use an exhaustive recovery function:

```ts
switch (error.code) {
  case "IDEMPOTENCY_KEY_REUSED":
    clearIntent();
    return "This key belongs to a different request. Request a fresh preview.";
  case "ALREADY_SETTLED":
    clearIntent();
    return "A selected receivable is already settled. Review the ledger.";
  case "PRICING_QUOTE_EXPIRED":
    clearIntent();
    return "A pricing quote expired. Create a fresh quote and preview.";
  default:
    return undefined;
}
```

Only known terminal/conflict outcomes clear the key. Status 409 alone does not choose a recovery path.

- [ ] **Step 7: Verify Settlement workflow**

Run the Step 2 command and `npm --prefix frontend run typecheck`. Expected: PASS.

- [ ] **Step 8: Commit race-safe Settlement state**

```bash
git add -- frontend/src/SettlementWorkspace.tsx frontend/src/SettlementWorkspace.test.tsx
git commit -m "fix(frontend): sequence settlement intent requests"
```

### Task 4: Sequence details/ledger and align role visibility

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/SettlementWorkspace.tsx`
- Test: `frontend/src/App.test.tsx`
- Test: `frontend/src/SettlementWorkspace.test.tsx`

**Interfaces:**
- Produces: ledger visible for OPERATOR, ANALYST, ADMIN, and AUDITOR.
- Retains: URL search parameters as the canonical filter/page state.

- [ ] **Step 1: Add failing role and response-race tests**

For each authorized role assert the ledger mounts and performs one statement request. Resolve an older filter/page response after a newer response and assert the visible table still matches the URL's newer filters/page. Repeat for settlement hash detail IDs.

- [ ] **Step 2: Run tests red**

```bash
npm --prefix frontend run test -- --run src/App.test.tsx src/SettlementWorkspace.test.tsx
```

- [ ] **Step 3: Match the backend permission matrix**

Use:

```ts
const canViewLedger = session.roles.some((role) =>
  ["OPERATOR", "ANALYST", "ADMIN", "AUDITOR"].includes(role),
);
```

Pass `showLedger={canViewLedger}`.

- [ ] **Step 4: Abort and sequence ledger/detail reads**

In each effect, create an `AbortController`, capture the canonical request key (`search.toString()` or settlement ID), and ignore abort errors. Cleanup aborts the request. Clear stale error state before a new request, and install data only for the captured current key.

- [ ] **Step 5: Preserve URL restoration exactly**

Keep `popstate` as the source for browser Back/Forward. A filter change resets `page=0`; paging preserves every filter. Dates remain ISO instants and display without changing the query value.

- [ ] **Step 6: Verify roles, races, and build**

```bash
npm --prefix frontend run test -- --run
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run build
```

Expected: PASS.

- [ ] **Step 7: Commit ledger integrity**

```bash
git add -- frontend/src/App.tsx frontend/src/SettlementWorkspace.tsx frontend/src/App.test.tsx frontend/src/SettlementWorkspace.test.tsx
git commit -m "fix(frontend): align ledger roles and response order"
```
