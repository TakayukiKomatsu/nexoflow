import { afterEach, describe, expect, it, vi } from "vitest";
import { loadSession } from "../session";
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
  productType: "MERCANTILE_INVOICE",
  dueDate: "2030-02-15",
  settlementCurrency: "BRL",
} as const;

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
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
  it("uses a status fallback when an error response has no problem detail", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => jsonResponse({}, 503)),
    );

    await expect(api.previewSettlement(["quote-1"], "token")).rejects.toEqual(
      expect.objectContaining({
        status: 503,
        message: "Request failed (503).",
      }),
    );
  });

  it("uses a status fallback when an error response is not JSON", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() =>
        Promise.resolve(new Response("upstream unavailable", { status: 502 })),
      ),
    );

    await expect(api.previewSettlement(["quote-1"], "token")).rejects.toEqual(
      expect.objectContaining({
        status: 502,
        message: "Request failed (502).",
      }),
    );
  });

  it("clears the shared session and announces expiry for any 401 response", async () => {
    localStorage.setItem(
      "srm-session",
      JSON.stringify({
        accessToken: "token",
        expiresAt: Date.now() + 60_000,
        email: "operator@srm.local",
        roles: ["OPERATOR"],
      }),
    );
    const expired = vi.fn();
    window.addEventListener("srm:session-expired", expired, { once: true });
    vi.stubGlobal(
      "fetch",
      vi.fn(() =>
        jsonResponse(
          {
            status: 401,
            code: "AUTHENTICATION_REQUIRED",
            detail: "Authentication is required.",
          },
          401,
        ),
      ),
    );

    await expect(
      api.previewSettlement(["quote-1"], "token"),
    ).rejects.toBeInstanceOf(ApiError);

    expect(localStorage.getItem("srm-session")).toBeNull();
    expect(expired).toHaveBeenCalledOnce();
  });
});

it("discards malformed persisted session JSON", () => {
  localStorage.setItem("srm-session", "{malformed");

  expect(loadSession()).toBeUndefined();
  expect(localStorage.getItem("srm-session")).toBeNull();
});

it("returns a session after loading the authenticated actor profile", async () => {
  const fetchMock = vi
    .fn()
    .mockImplementationOnce(() =>
      jsonResponse({ accessToken: "token", expiresIn: 900 }),
    )
    .mockImplementationOnce(() =>
      jsonResponse({ email: "operator@srm.local", roles: ["OPERATOR"] }),
    );
  vi.stubGlobal("fetch", fetchMock);

  await expect(
    api.login({ email: "operator@srm.local", password: "correct-password" }),
  ).resolves.toEqual({
    accessToken: "token",
    expiresAt: expect.any(Number),
    email: "operator@srm.local",
    roles: ["OPERATOR"],
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
