/**
 * E2E-001 – Operator financial critical path
 *
 * Exercises the full operator workflow against a real backend, PostgreSQL, and
 * browser. No mocks; no fixed sleeps; only web-first assertions and event-
 * driven waits.
 */

import { expect, test } from "@playwright/test";
import type { Request } from "@playwright/test";

// ---------------------------------------------------------------------------
// Fixed test fixtures
// ---------------------------------------------------------------------------

const OPERATOR_EMAIL = "operator@srm.local";
const OPERATOR_PASSWORD = "local-review-only-not-a-real-password";
const ADMIN_EMAIL = "admin@srm.local";
const ADMIN_PASSWORD = "local-admin-review-only-not-a-real-password";
const ASSIGNOR_ID = "00000000-0000-0000-0000-000000000201";

// Issue date must be in the past relative to the running backend clock so the
// due date constraint (due_date > issue_date) holds.
const ISSUE_DATE = "2024-01-01";
const DUE_DATE = "2030-02-14";
const SETTLEMENT_CURRENCY = "BRL"; // same as face currency → IDENTITY, no FX rate required

// ---------------------------------------------------------------------------
// E2E-001
// ---------------------------------------------------------------------------

test("E2E-001 operator financial critical path", async ({ page, request }) => {
  // ─── Step 1: Login as OPERATOR ───────────────────────────────────────────

  const navigation = await page.goto("/");
  expect(navigation?.headers()["content-security-policy"]).toContain(
    "frame-ancestors 'none'",
  );
  expect(navigation?.headers()["x-frame-options"]).toBe("DENY");
  expect(navigation?.headers()["x-content-type-options"]).toBe("nosniff");
  expect(navigation?.headers()["referrer-policy"]).toBe("no-referrer");

  const email = page.getByLabel("Email");
  const password = page.getByLabel("Password");
  const signIn = page.getByRole("button", { name: "Sign in" });
  await email.fill(OPERATOR_EMAIL);
  await email.focus();
  await page.keyboard.press("Tab");
  await expect(password).toBeFocused();
  await password.fill(OPERATOR_PASSWORD);
  await page.keyboard.press("Tab");
  await expect(signIn).toBeFocused();
  await page.keyboard.press("Enter");

  await expect(
    page.getByRole("heading", { name: "Live receivable pricing" }),
  ).toBeVisible();

  // ─── Step 2: Observe and change the live server simulation ───────────────

  await page.getByLabel("Issue date").fill(ISSUE_DATE);
  await page.getByLabel("Due date").fill(DUE_DATE);
  await page
    .getByLabel("Assignor ID (for explicit registration)")
    .fill(ASSIGNOR_ID);

  const simulationSection = page.getByRole("region", {
    name: "Server simulation",
  });
  const simulationSettlementAmount = simulationSection
    .locator("output")
    .first();

  // The same-currency invoice fixture must display the exact server result.
  await expect(simulationSettlementAmount).toHaveText("975.61");
  await expect(simulationSection.getByText("Base rate")).toBeVisible();
  await expect(simulationSection.getByText("Strategy")).toBeVisible();

  const productSelector = page.getByLabel(/Product/);

  const chequeSimulationRequests: string[] = [];
  const trackChequeSimulation = (request: Request) => {
    if (
      request.method() === "POST" &&
      request.url().includes("/api/v1/pricing-simulations") &&
      request.postDataJSON().productType === "POST_DATED_CHEQUE"
    ) {
      chequeSimulationRequests.push(request.url());
    }
  };
  page.on("request", trackChequeSimulation);

  const chequeSimulationResponse = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().includes("/api/v1/pricing-simulations") &&
      response.request().postDataJSON().productType === "POST_DATED_CHEQUE",
  );

  await productSelector.selectOption("POST_DATED_CHEQUE");
  await chequeSimulationResponse;

  await expect(productSelector).toHaveValue("POST_DATED_CHEQUE");
  await expect(simulationSettlementAmount).toHaveText("966.18");
  expect(chequeSimulationRequests).toHaveLength(1);
  page.off("request", trackChequeSimulation);

  // ─── Step 3: Register one Receivable ─────────────────────────────────────

  await page.getByRole("button", { name: "Register receivable" }).click();

  const workflowFeedback = page.locator("#workflow-feedback");
  await expect(workflowFeedback).toBeVisible();
  await expect(workflowFeedback).toContainText("Receivable");
  await expect(workflowFeedback).toContainText("registered");

  // ─── Step 4: Create one Quote and inspect its complete breakdown ──────────

  await page.getByRole("button", { name: "Create quote" }).click();

  await expect(workflowFeedback).toContainText("Quote");
  await expect(workflowFeedback).toContainText("created");

  // Quote breakdown article appears inside the Server simulation card
  // The issued quote preserves the exact authoritative cheque simulation.
  const quoteArticle = simulationSection.getByRole("article").first();
  await expect(quoteArticle).toBeVisible();
  await expect(quoteArticle.getByText("Settlement amount")).toBeVisible();
  await expect(quoteArticle.getByText("966.18 BRL")).toBeVisible();
  await expect(quoteArticle.getByText("Product type")).toBeVisible();
  await expect(quoteArticle.getByText("POST_DATED_CHEQUE")).toBeVisible();
  await expect(quoteArticle.getByText("Due date")).toBeVisible();
  await expect(quoteArticle.getByText(DUE_DATE)).toBeVisible();
  await expect(quoteArticle.getByText("Base rate")).toBeVisible();
  await expect(quoteArticle.getByText("Expires at")).toBeVisible();
  await expect(quoteArticle.getByText("Status")).toBeVisible();

  // ─── Step 5: Select the Quote and request a fresh Preview ─────────────────

  const settlementSection = page.getByRole("region", {
    name: "Settlement intent",
  });

  const quoteCheckbox = settlementSection.getByRole("checkbox").first();
  await expect(quoteCheckbox).toBeVisible();
  await quoteCheckbox.check();

  await settlementSection
    .getByRole("button", { name: "Request server preview" })
    .click();

  const previewDiv = page.getByLabel("Server settlement preview");
  await expect(previewDiv).toBeVisible();
  await expect(previewDiv.locator("output")).toBeVisible();

  // ─── Step 6: Capture Settlement request – assert Idempotency-Key ──────────

  // Attach the listener BEFORE clicking so we never miss the event
  const settlementRequestPromise = page.waitForRequest(
    (req) =>
      req.url().includes("/api/v1/settlements") &&
      !req.url().includes("/reversals") &&
      req.method() === "POST",
  );
  const settlementResponsePromise = page.waitForResponse(
    (response) =>
      response.url().includes("/api/v1/settlements") &&
      !response.url().includes("/reversals") &&
      response.request().method() === "POST",
  );
  const automaticLedgerRefresh = page.waitForResponse(
    (response) =>
      response.url().includes("/api/v1/settlement-statements") &&
      response.request().method() === "GET",
  );

  await previewDiv.getByRole("button", { name: "Confirm settlement" }).click();

  const capturedRequest = await settlementRequestPromise;

  const originalResponse = await settlementResponsePromise;
  expect(originalResponse.status()).toBe(201);
  await automaticLedgerRefresh;

  const capturedKey = capturedRequest.headers()["idempotency-key"];
  expect(capturedKey).toMatch(
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
  );

  // response.json() returns any in Playwright – read properties directly
  const capturedBody = capturedRequest.postDataJSON();
  expect(Object.keys(capturedBody)).toEqual(["quoteIds"]);
  expect(Array.isArray(capturedBody.quoteIds)).toBe(true);
  expect(capturedBody.quoteIds).toHaveLength(1);
  expect(capturedBody).not.toHaveProperty("totalAmount");
  expect(capturedBody).not.toHaveProperty("settlementAmount");

  // ─── Step 6b: Retry reuses exact key and body ────────────────────────────
  //
  // Prove that an unknown-outcome retry would submit the same idempotency key.
  // Call the settlements endpoint a second time with the identical key and body;
  // the server must respond 201 with Idempotent-Replay: true.

  const operatorToken = await page.evaluate<string>(() => {
    const raw = localStorage.getItem("srm-session");
    if (!raw) throw new Error("srm-session not found in localStorage");
    const session = JSON.parse(raw) as { accessToken: string };
    return session.accessToken;
  });

  const retryResponse = await request.post("/api/v1/settlements", {
    data: capturedBody,
    headers: {
      Authorization: `Bearer ${operatorToken}`,
      "Idempotency-Key": capturedKey,
      "Content-Type": "application/json",
    },
  });
  expect(retryResponse.status()).toBe(201);
  expect(retryResponse.headers()["idempotent-replay"]).toBe("true");

  const retryData = await retryResponse.json();
  const retrySettlementId: string = retryData.settlementId;
  expect(retrySettlementId).toMatch(/^[0-9a-f-]{36}$/i);

  // ─── Step 7: Observe the Settlement ID ────────────────────────────────────

  const confirmationStatus = settlementSection.getByRole("status");
  await expect(confirmationStatus).toContainText("Settlement ID");
  await expect(confirmationStatus).toContainText("confirmed");

  const settlementLink = confirmationStatus.getByRole("link");
  const settlementHref = await settlementLink.getAttribute("href");
  expect(settlementHref).toMatch(/^#settlement-[0-9a-f-]+$/i);
  const settlementId = settlementHref!.replace("#settlement-", "");
  expect(settlementId).toBe(retrySettlementId);
  await expect(quoteCheckbox).toBeHidden();
  await expect(
    settlementSection.getByText(
      /Create one or more quotes before requesting settlement/,
    ),
  ).toBeVisible();

  // ─── Step 8: Navigate ledger filters and assert URL restoration ───────────

  const ledgerSection = page.getByRole("region", {
    name: "Signed settlement statement",
  });
  await expect(ledgerSection).toBeVisible();

  const currencyFilter = ledgerSection.getByLabel("Ledger settlement currency");
  const productFilter = ledgerSection.getByLabel("Ledger product");
  const assignorFilter = ledgerSection.getByLabel("Ledger assignor ID");
  const assetFilter = ledgerSection.getByLabel("Ledger asset currency");
  const fromFilter = ledgerSection.getByLabel("From filter");
  const toFilter = ledgerSection.getByLabel("To filter");
  const pageSize = ledgerSection.getByLabel("Page size");
  const historyLength = await page.evaluate(() => window.history.length);
  const filteredLedgerResponse = page.waitForResponse((response) => {
    if (!response.url().includes("/api/v1/settlement-statements")) return false;
    const parameters = new URL(response.url()).searchParams;
    return (
      parameters.get("settlementCurrency") === SETTLEMENT_CURRENCY &&
      parameters.get("productType") === "POST_DATED_CHEQUE" &&
      parameters.get("assignorId") === ASSIGNOR_ID &&
      parameters.get("assetCurrency") === "BRL" &&
      parameters.has("from") &&
      parameters.has("to") &&
      parameters.get("size") === "25"
    );
  });

  // Typing replaces one shareable URL entry and debounces one complete vector.
  await currencyFilter.fill(SETTLEMENT_CURRENCY);
  await productFilter.fill("POST_DATED_CHEQUE");
  await assignorFilter.fill(ASSIGNOR_ID);
  await assetFilter.fill("BRL");
  await fromFilter.fill("2020-01-01T00:00");
  await toFilter.fill("2040-01-01T00:00");
  await pageSize.selectOption("25");
  await filteredLedgerResponse;
  await expect(page).toHaveURL(
    new RegExp(`settlementCurrency=${SETTLEMENT_CURRENCY}`),
  );
  await expect(page).toHaveURL(/productType=POST_DATED_CHEQUE/);
  await expect(page).toHaveURL(new RegExp(`assignorId=${ASSIGNOR_ID}`));
  await expect(page).toHaveURL(/assetCurrency=BRL/);
  await expect(page).toHaveURL(/page=0/);
  expect(await page.evaluate(() => window.history.length)).toBe(historyLength);
  const filteredUrl = page.url();

  // A deliberate navigation is restorable; individual keystrokes are not.
  await page.evaluate(() => {
    window.history.pushState(null, "", window.location.pathname);
    window.dispatchEvent(new PopStateEvent("popstate"));
  });
  await expect(currencyFilter).toHaveValue("");
  await page.goBack();
  await expect(page).toHaveURL(filteredUrl);
  await expect(currencyFilter).toHaveValue(SETTLEMENT_CURRENCY);
  await expect(productFilter).toHaveValue("POST_DATED_CHEQUE");

  // ─── Step 9: Login as ADMIN and reverse via the API ──────────────────────

  // No UI reversal control exists; the plan specifies ADMIN API reversal.

  const adminLoginResponse = await request.post("/api/v1/auth/login", {
    data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD },
  });
  expect(adminLoginResponse.status()).toBe(200);
  const adminAuth = await adminLoginResponse.json();
  const adminToken: string = adminAuth.accessToken;

  const reversalResponse = await request.post(
    `/api/v1/settlements/${settlementId}/reversals`,
    {
      data: { reason: "E2E-001 test reversal" },
      headers: {
        Authorization: `Bearer ${adminToken}`,
        "Idempotency-Key": crypto.randomUUID(),
        "Content-Type": "application/json",
      },
    },
  );
  expect(reversalResponse.status()).toBe(201);
  const reversalData = await reversalResponse.json();
  const reversalId: string = reversalData.reversalId;
  const reversalSettlementId: string = reversalData.settlementId;
  expect(reversalId).toMatch(/^[0-9a-f-]{36}$/i);
  expect(reversalSettlementId).toBe(settlementId);

  // ─── Step 10: Assert signed ledger entries link to the Settlement ────────

  // Refresh via the URL-backed filter so the browser receives the API reversal.
  const unfilteredLedgerResponse = page.waitForResponse(
    (response) =>
      response.url().includes("/api/v1/settlement-statements") &&
      !response.url().includes("settlementCurrency="),
  );
  await currencyFilter.fill("");
  await currencyFilter.press("Tab");
  await unfilteredLedgerResponse;

  const refreshedLedgerResponse = page.waitForResponse(
    (response) =>
      response.url().includes("/api/v1/settlement-statements") &&
      new URL(response.url()).searchParams.get("settlementCurrency") ===
        SETTLEMENT_CURRENCY,
  );
  await currencyFilter.fill(SETTLEMENT_CURRENCY);
  await currencyFilter.press("Tab");
  await refreshedLedgerResponse;
  await expect(page).toHaveURL(
    new RegExp(`settlementCurrency=${SETTLEMENT_CURRENCY}`),
  );

  const tableRows = ledgerSection.locator("tbody tr");
  await expect(tableRows.first()).toBeVisible();

  // Both rows must link to the same settlement ID
  const settlementRow = tableRows
    .filter({ hasText: "SETTLEMENT" })
    .filter({ hasText: settlementId });
  const reversalRow = tableRows
    .filter({ hasText: "REVERSAL" })
    .filter({ hasText: settlementId });

  await expect(settlementRow).toBeVisible();
  await expect(reversalRow).toBeVisible();

  // SETTLEMENT signed amount is positive, REVERSAL is negative
  const settlementAmountText = await settlementRow
    .locator("td")
    .nth(1)
    .textContent();
  expect(settlementAmountText).toBeTruthy();
  expect(settlementAmountText!.trim().startsWith("-")).toBe(false);

  const reversalAmountText = await reversalRow
    .locator("td")
    .nth(1)
    .textContent();
  expect(reversalAmountText).toBeTruthy();
  expect(reversalAmountText!.trim().startsWith("-")).toBe(true);
});
