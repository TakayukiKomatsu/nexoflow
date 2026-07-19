import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api, type ProblemDetail } from "./client";

const jsonResponse = (body: unknown, status = 200) =>
  Promise.resolve(
    new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/problem+json" },
    }),
  );

const simulationInput = {
  faceAmount: "1000.00",
  faceCurrency: "BRL",
  productType: "INVOICE",
  dueDate: "2030-02-15",
  settlementCurrency: "BRL",
};

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("API client error contract", () => {
  it("preserves RFC 9457 status, code, detail, and correlation ID", async () => {
    const problem = {
      status: 409,
      detail: "The idempotency key was already used for another request.",
      code: "IDEMPOTENCY_KEY_REUSED",
      correlationId: "corr-1",
      violations: [{ field: "idempotencyKey", message: "must be unique" }],
    } satisfies ProblemDetail;
    vi.stubGlobal(
      "fetch",
      vi.fn(() => jsonResponse(problem, 409)),
    );

    const result = api.previewSettlement(["quote-1"], "token");

    await expect(result).rejects.toEqual(
      expect.objectContaining({
        status: 409,
        code: "IDEMPOTENCY_KEY_REUSED",
        message: problem.detail,
        correlationId: "corr-1",
      }),
    );
  });

  it("leaves network failures classified as their native error", async () => {
    const networkError = new TypeError("Failed to fetch");
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.reject(networkError)),
    );

    const result = api.previewSettlement(["quote-1"], "token");

    await expect(result).rejects.toBe(networkError);
    await expect(result).rejects.not.toBeInstanceOf(ApiError);
  });

  it("preserves AbortError without wrapping it as ApiError", async () => {
    const abortError = new DOMException(
      "The operation was aborted.",
      "AbortError",
    );
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.reject(abortError)),
    );

    const result = api.previewSettlement(["quote-1"], "token");

    await expect(result).rejects.toBe(abortError);
    await expect(result).rejects.not.toBeInstanceOf(ApiError);
  });
});

describe("API client cancellation contract", () => {
  it.each([
    [
      "pricing simulation",
      (signal: AbortSignal) => api.simulate(simulationInput, "token", signal),
    ],
    [
      "settlement preview",
      (signal: AbortSignal) =>
        api.previewSettlement(["quote-1"], "token", signal),
    ],
    [
      "settlement creation",
      (signal: AbortSignal) =>
        api.settle(["quote-1"], "idempotency-key", "token", signal),
    ],
    [
      "settlement detail",
      (signal: AbortSignal) => api.settlement("settlement-1", "token", signal),
    ],
    [
      "settlement statement",
      (signal: AbortSignal) =>
        api.statement(new URLSearchParams("page=0"), "token", signal),
    ],
  ])("passes AbortSignal to %s requests", async (_name, invoke) => {
    const fetchMock = vi.fn(() => jsonResponse({}));
    vi.stubGlobal("fetch", fetchMock);
    const signal = new AbortController().signal;

    await invoke(signal);

    expect(fetchMock).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({ signal }),
    );
  });
});
