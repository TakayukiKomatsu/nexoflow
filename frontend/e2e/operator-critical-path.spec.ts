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

  await page.goto("/");
  await page.getByLabel("Email").fill(OPERATOR_EMAIL);
  await page.getByLabel("Password").fill(OPERATOR_PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();

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

  await previewDiv.getByRole("button", { name: "Confirm settlement" }).click();

  const capturedRequest = await settlementRequestPromise;

  const originalResponse = await settlementResponsePromise;
  expect(originalResponse.status()).toBe(201);

  const capturedKey = capturedRequest.headers()["idempotency-key"];
  expect(capturedKey).toMatch(
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
  );

  // response.json() returns any in Playwright – read properties directly
  const capturedBody = capturedRequest.postDataJSON();
  expect(Array.isArray(capturedBody.quoteIds)).toBe(true);
  expect(capturedBody.quoteIds).toHaveLength(1);

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

  // ─── Step 8: Navigate ledger filters and assert URL restoration ───────────

  const ledgerSection = page.getByRole("region", {
    name: "Signed settlement statement",
  });
  await expect(ledgerSection).toBeVisible();

  const currencyFilter = ledgerSection.getByLabel("Ledger settlement currency");

  // Apply a filter – pushState updates the URL
  await currencyFilter.fill(SETTLEMENT_CURRENCY);
  await currencyFilter.press("Tab");
  await expect(page).toHaveURL(
    new RegExp(`settlementCurrency=${SETTLEMENT_CURRENCY}`),
  );
  await expect(page).toHaveURL(/page=0/);

  // Back restores the pre-filter URL
  await page.goBack();
  await expect(page).not.toHaveURL(/settlementCurrency=/);

  // Forward restores the filtered URL
  await page.goForward();
  await expect(page).toHaveURL(
    new RegExp(`settlementCurrency=${SETTLEMENT_CURRENCY}`),
  );

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
