import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
} from "@testing-library/react";
import { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SettlementWorkspace } from "./SettlementWorkspace";
import type { PricingQuote, Session } from "./api/client";
import { SESSION_EXPIRED_EVENT } from "./session";

const session: Session = {
  accessToken: "token",
  expiresAt: Date.now() + 60_000,
  email: "operator@srm.local",
  roles: ["OPERATOR"],
};
const quote: PricingQuote = {
  id: "00000000-0000-0000-0000-000000000801",
  receivableId: "00000000-0000-0000-0000-000000000701",
  expiresAt: "2030-01-16T12:00:00Z",
  status: "ACTIVE",
  pricing: { settlementAmount: "1900.00", settlementCurrency: "BRL" },
} as PricingQuote;
const quoteB: PricingQuote = {
  ...quote,
  id: "00000000-0000-0000-0000-000000000802",
  receivableId: "00000000-0000-0000-0000-000000000702",
  pricing: { ...quote.pricing, settlementAmount: "200.00" },
};
const preview = {
  items: [
    {
      quoteId: quote.id,
      receivableId: quote.receivableId,
      settlementAmount: "1900.00",
    },
  ],
  settlementCurrency: "BRL",
  totalAmount: "1900.00",
  asOf: "2030-01-15T12:00:00Z",
  earliestExpiry: "2030-01-16T12:00:00Z",
};
function json(body: unknown) {
  return Promise.resolve(
    new Response(JSON.stringify(body), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }),
  );
}
function problem(code: string, status = 409) {
  return Promise.resolve(
    new Response(JSON.stringify({ code, status, detail: code }), {
      status,
      headers: { "Content-Type": "application/problem+json" },
    }),
  );
}
function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  vi.useRealTimers();
  localStorage.clear();
  window.history.replaceState(null, "", "/");
});

describe("UI-SETTLE-004 retry-safe settlement intent", () => {
  it("announces preview loading on the busy settlement region", async () => {
    const pendingPreview = deferred<Response>();
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.includes("settlement-statements")) {
          return json({ entries: [], page: 0, size: 50, hasNext: false });
        }
        if (url.includes("settlement-previews")) return pendingPreview.promise;
        throw new Error(`Unexpected ${url}`);
      }),
    );
    render(<SettlementWorkspace session={session} quotes={[quote]} />);
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(
      screen.getByRole("button", { name: "Request server preview" }),
    );

    const settlement = screen
      .getByRole("heading", { name: "Settlement intent" })
      .closest("section");
    expect(settlement).toHaveAttribute("aria-busy", "true");
    expect(
      screen.getByRole("button", { name: "Requesting server preview…" }),
    ).toBeDisabled();

    pendingPreview.resolve(await json(preview));
    await screen.findByRole("button", { name: "Confirm settlement" });
    expect(settlement).toHaveAttribute("aria-busy", "false");
  });

  it("removes consumed quotes and refreshes the signed ledger after settlement", async () => {
    let statementRequests = 0;
    const fetchMock = vi.fn((url: string) => {
      if (url.includes("settlement-statements")) {
        statementRequests += 1;
        return json({ entries: [], page: 0, size: 50, hasNext: false });
      }
      if (url.includes("settlement-previews")) return json(preview);
      if (url.endsWith("/settlements")) {
        return json({
          ...preview,
          settlementId: "00000000-0000-0000-0000-000000000804",
          status: "COMPLETED",
          completedAt: "2030-01-15T12:01:00Z",
        });
      }
      throw new Error(`Unexpected ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    function SettlementHarness() {
      const [activeQuotes, setActiveQuotes] = useState([quote]);
      return (
        <SettlementWorkspace
          session={session}
          quotes={activeQuotes}
          onSettled={(consumedIds) =>
            setActiveQuotes((current) =>
              current.filter(({ id }) => !consumedIds.includes(id)),
            )
          }
        />
      );
    }

    render(<SettlementHarness />);
    await screen.findByRole("heading", {
      name: "Signed settlement statement",
    });
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(
      screen.getByRole("button", { name: "Request server preview" }),
    );
    await screen.findByRole("button", { name: "Confirm settlement" });
    fireEvent.click(screen.getByRole("button", { name: "Confirm settlement" }));

    await screen.findByRole("link", {
      name: "00000000-0000-0000-0000-000000000804",
    });
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
    expect(
      screen.getByText(
        /Create one or more quotes before requesting settlement/i,
      ),
    ).toBeInTheDocument();
    expect(statementRequests).toBe(2);
  });

  it("reuses the persisted key and submits quote IDs only after a lost response", async () => {
    let settlementCalls = 0;
    const fetchMock = vi.fn((url: string) => {
      if (url.includes("settlement-statements"))
        return json({ entries: [], page: 0, size: 50, hasNext: false });
      if (url.includes("settlement-previews")) return json(preview);
      if (url.includes("settlements")) {
        settlementCalls += 1;
        return settlementCalls === 1
          ? Promise.reject(new TypeError("Network lost"))
          : json({
              ...preview,
              settlementId: "S804",
              status: "COMPLETED",
              completedAt: "2030-01-15T12:01:00Z",
            });
      }
      throw new Error(`Unexpected ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<SettlementWorkspace session={session} quotes={[quote]} />);
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(
      screen.getByRole("button", { name: "Request server preview" }),
    );
    await screen.findByRole("button", { name: "Confirm settlement" });
    fireEvent.click(screen.getByRole("button", { name: "Confirm settlement" }));
    await screen.findByText(/Retry uses the same settlement intent/);
    fireEvent.click(screen.getByRole("button", { name: "Confirm settlement" }));
    await screen.findByRole("link", { name: "S804" });
    const requests = (
      fetchMock.mock.calls as unknown as Array<
        [string, { headers: Record<string, string>; body: string }]
      >
    ).filter(
      ([url]) =>
        String(url).includes("/settlements") &&
        !String(url).includes("statements"),
    );
    expect(requests).toHaveLength(2);
    expect(requests[0][1].headers["Idempotency-Key"]).toBe(
      requests[1][1].headers["Idempotency-Key"],
    );
    expect(JSON.parse(requests[1][1].body)).toEqual({ quoteIds: [quote.id] });
  });

  it("recovers a saved intent after reload only for the actor that created it", async () => {
    const fetchMock = vi.fn((url: string) => {
      if (url.includes("settlement-statements"))
        return json({ entries: [], page: 0, size: 50, hasNext: false });
      if (url.includes("settlement-previews")) return json(preview);
      if (url.includes("settlements"))
        return Promise.reject(new TypeError("Network lost"));
      throw new Error(`Unexpected ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);
    const first = render(
      <SettlementWorkspace session={session} quotes={[quote]} />,
    );
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(
      screen.getByRole("button", { name: "Request server preview" }),
    );
    await screen.findByRole("button", { name: "Confirm settlement" });
    fireEvent.click(screen.getByRole("button", { name: "Confirm settlement" }));
    await screen.findByText(/Retry uses the same settlement intent/);
    first.unmount();

    render(<SettlementWorkspace session={session} quotes={[quote]} />);
    expect(screen.getByRole("checkbox")).toBeChecked();
    expect(
      screen.getByText(/saved settlement intent was restored/i),
    ).toBeInTheDocument();
    cleanup();

    render(
      <SettlementWorkspace
        session={{ ...session, email: "other@srm.local" }}
        quotes={[quote]}
      />,
    );
    expect(screen.getByRole("checkbox")).not.toBeChecked();
    expect(
      screen.queryByText(/saved settlement intent was restored/i),
    ).not.toBeInTheDocument();
  });

  it("does not expose settlement controls to a read-only actor", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => json({ entries: [], page: 0, size: 50, hasNext: false })),
    );
    render(
      <SettlementWorkspace
        session={{ ...session, roles: ["ANALYST"] }}
        quotes={[quote]}
      />,
    );
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Request server preview" }),
    ).not.toBeInTheDocument();
    expect(screen.getByText(/cannot create them/i)).toBeInTheDocument();
  });

  it("keeps the newest ordered-selection preview when older requests finish later", async () => {
    const first = deferred<Response>();
    const second = deferred<Response>();
    let previewCalls = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.includes("settlement-statements"))
          return json({ entries: [], page: 0, size: 50, hasNext: false });
        if (url.includes("settlement-previews"))
          return previewCalls++ === 0 ? first.promise : second.promise;
        throw new Error(`Unexpected ${url}`);
      }),
    );
    render(<SettlementWorkspace session={session} quotes={[quote, quoteB]} />);

    fireEvent.click(screen.getAllByRole("checkbox")[0]);
    fireEvent.click(
      screen.getByRole("button", { name: "Request server preview" }),
    );
    fireEvent.click(screen.getAllByRole("checkbox")[1]);
    fireEvent.click(
      screen.getByRole("button", { name: "Request server preview" }),
    );
    second.resolve(
      await json({
        ...preview,
        items: [
          ...preview.items,
          {
            quoteId: quoteB.id,
            receivableId: quoteB.receivableId,
            settlementAmount: "200.00",
          },
        ],
        totalAmount: "2100.00",
      }),
    );
    expect(await screen.findByText("2100.00")).toBeInTheDocument();
    first.resolve(await json(preview));
    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.getByText("2100.00")).toBeInTheDocument();
  });

  it("disables confirmation when the installed preview reaches earliestExpiry", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2030-01-15T12:00:00Z"));
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.includes("settlement-statements"))
          return json({ entries: [], page: 0, size: 50, hasNext: false });
        if (url.includes("settlement-previews"))
          return json({
            ...preview,
            earliestExpiry: "2030-01-15T12:00:01Z",
          });
        throw new Error(`Unexpected ${url}`);
      }),
    );
    render(<SettlementWorkspace session={session} quotes={[quote]} />);
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(
      screen.getByRole("button", { name: "Request server preview" }),
    );
    await act(async () => {
      await Promise.resolve();
    });
    const confirm = screen.getByRole("button", {
      name: "Confirm settlement",
    });
    expect(confirm).toBeEnabled();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1_000);
    });
    expect(confirm).toBeDisabled();
    expect(screen.getByText(/preview has expired/i)).toBeInTheDocument();
  });

  it("aborts settlement after ten seconds and retains the exact stored intent", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2030-01-15T12:00:00Z"));
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string, init?: RequestInit) => {
        if (url.includes("settlement-statements"))
          return json({ entries: [], page: 0, size: 50, hasNext: false });
        if (url.includes("settlement-previews")) return json(preview);
        if (url.includes("settlements"))
          return new Promise<Response>((_resolve, reject) => {
            init?.signal?.addEventListener("abort", () =>
              reject(new DOMException("Aborted", "AbortError")),
            );
          });
        throw new Error(`Unexpected ${url}`);
      }),
    );
    render(<SettlementWorkspace session={session} quotes={[quote]} />);
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(
      screen.getByRole("button", { name: "Request server preview" }),
    );
    await act(async () => {
      await Promise.resolve();
    });
    fireEvent.click(screen.getByRole("button", { name: "Confirm settlement" }));
    const storedBefore = localStorage.getItem("srm-settlement-intent");
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });
    expect(
      screen.getByText(/timed out and the outcome is unknown/i),
    ).toBeInTheDocument();
    expect(localStorage.getItem("srm-settlement-intent")).toBe(storedBefore);
  });

  it.each([
    ["IDEMPOTENCY_KEY_REUSED", 409, /key belongs to a different request/i],
    ["ALREADY_SETTLED", 409, /already settled.*review the ledger/i],
    [
      "PRICING_QUOTE_EXPIRED",
      410,
      /pricing quote expired.*fresh quote and preview/i,
    ],
  ])(
    "clears settlement intent and preview for terminal code %s",
    async (code, status, guidance) => {
      vi.stubGlobal(
        "fetch",
        vi.fn((url: string) => {
          if (url.includes("settlement-statements"))
            return json({ entries: [], page: 0, size: 50, hasNext: false });
          if (url.includes("settlement-previews")) return json(preview);
          if (url.includes("settlements")) return problem(code, status);
          throw new Error(`Unexpected ${url}`);
        }),
      );
      render(<SettlementWorkspace session={session} quotes={[quote]} />);
      fireEvent.click(screen.getByRole("checkbox"));
      fireEvent.click(
        screen.getByRole("button", { name: "Request server preview" }),
      );
      await screen.findByRole("button", { name: "Confirm settlement" });
      fireEvent.click(
        screen.getByRole("button", { name: "Confirm settlement" }),
      );
      expect(await screen.findByText(guidance)).toBeInTheDocument();
      expect(localStorage.getItem("srm-settlement-intent")).toBeNull();
      expect(
        screen.queryByLabelText("Server settlement preview"),
      ).not.toBeInTheDocument();
    },
  );

  it("retains the key for an unclassified 409 instead of recovering by status alone", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.includes("settlement-statements"))
          return json({ entries: [], page: 0, size: 50, hasNext: false });
        if (url.includes("settlement-previews")) return json(preview);
        if (url.includes("settlements"))
          return problem("UNCLASSIFIED_CONFLICT", 409);
        throw new Error(`Unexpected ${url}`);
      }),
    );
    render(<SettlementWorkspace session={session} quotes={[quote]} />);
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(
      screen.getByRole("button", { name: "Request server preview" }),
    );
    await screen.findByRole("button", { name: "Confirm settlement" });
    fireEvent.click(screen.getByRole("button", { name: "Confirm settlement" }));
    expect(
      await screen.findByText(/Retry uses the same settlement intent/),
    ).toBeInTheDocument();
    expect(localStorage.getItem("srm-settlement-intent")).not.toBeNull();
  });

  it("requires explicit cancellation before a retained intent can be replaced by another selection", async () => {
    let settlementCalls = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.includes("settlement-statements"))
          return json({ entries: [], page: 0, size: 50, hasNext: false });
        if (url.includes("settlement-previews")) return json(preview);
        if (url.includes("settlements")) {
          settlementCalls += 1;
          return Promise.reject(new TypeError("Network lost"));
        }
        throw new Error(`Unexpected ${url}`);
      }),
    );
    render(<SettlementWorkspace session={session} quotes={[quote, quoteB]} />);
    fireEvent.click(screen.getAllByRole("checkbox")[0]);
    fireEvent.click(
      screen.getByRole("button", { name: "Request server preview" }),
    );
    await screen.findByRole("button", { name: "Confirm settlement" });
    fireEvent.click(screen.getByRole("button", { name: "Confirm settlement" }));
    await screen.findByText(/Retry uses the same settlement intent/);
    const storedIntent = localStorage.getItem("srm-settlement-intent");

    fireEvent.click(screen.getAllByRole("checkbox")[0]);
    fireEvent.click(screen.getAllByRole("checkbox")[1]);
    expect(
      screen.getByText(/Cancel the saved settlement intent/i),
    ).toBeInTheDocument();
    fireEvent.click(
      screen.getByRole("button", { name: "Request server preview" }),
    );
    const confirm = await screen.findByRole("button", {
      name: "Confirm settlement",
    });
    expect(confirm).toBeDisabled();
    fireEvent.click(confirm);
    expect(localStorage.getItem("srm-settlement-intent")).toBe(storedIntent);
    expect(settlementCalls).toBe(1);
  });

  it("checks exact expiry synchronously before the timer state can commit", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2030-01-15T12:00:00Z"));
    let settlementCalls = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.includes("settlement-statements"))
          return json({ entries: [], page: 0, size: 50, hasNext: false });
        if (url.includes("settlement-previews"))
          return json({
            ...preview,
            earliestExpiry: "2030-01-15T12:00:01Z",
          });
        if (url.includes("settlements")) {
          settlementCalls += 1;
          return json({});
        }
        throw new Error(`Unexpected ${url}`);
      }),
    );
    render(<SettlementWorkspace session={session} quotes={[quote]} />);
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(
      screen.getByRole("button", { name: "Request server preview" }),
    );
    await act(async () => {
      await Promise.resolve();
    });
    const confirm = screen.getByRole("button", {
      name: "Confirm settlement",
    });
    vi.setSystemTime(new Date("2030-01-15T12:00:01Z"));
    fireEvent.click(confirm);
    expect(
      screen.getByText(/preview is stale or expired/i),
    ).toBeInTheDocument();
    expect(settlementCalls).toBe(0);
  });
});

describe("UI-LEDGER-006 signed reversal statement", () => {
  it("replaces URL filter state and debounces statement requests while typing", async () => {
    vi.useFakeTimers();
    const fetchMock = vi.fn((_url: string) =>
      json({ entries: [], page: 0, size: 50, hasNext: false }),
    );
    vi.stubGlobal("fetch", fetchMock);
    const pushState = vi.spyOn(window.history, "pushState");
    const replaceState = vi.spyOn(window.history, "replaceState");
    render(<SettlementWorkspace session={session} quotes={[]} />);
    await act(async () => {
      await Promise.resolve();
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);

    const currency = screen.getByLabelText("Ledger settlement currency");
    fireEvent.change(currency, { target: { value: "b" } });
    fireEvent.change(currency, { target: { value: "br" } });
    fireEvent.change(currency, { target: { value: "brl" } });

    expect(window.location.search).toContain("settlementCurrency=BRL");
    expect(window.location.search).toContain("page=0");
    expect(replaceState).toHaveBeenCalled();
    expect(pushState).not.toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledTimes(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(299);
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1][0]).toContain("settlementCurrency=BRL");
  });

  it("renders UTC instants in local datetime fields without shifting the filter", () => {
    const previousTimeZone = process.env.TZ;
    process.env.TZ = "America/Sao_Paulo";
    try {
      vi.stubGlobal(
        "fetch",
        vi.fn(() => json({ entries: [], page: 0, size: 50, hasNext: false })),
      );
      window.history.replaceState(
        null,
        "",
        "/?from=2030-01-01T12%3A00%3A00.000Z",
      );

      render(<SettlementWorkspace session={session} quotes={[]} />);

      expect(screen.getByLabelText("From filter")).toHaveValue(
        "2030-01-01T09:00",
      );
    } finally {
      if (previousTimeZone === undefined) delete process.env.TZ;
      else process.env.TZ = previousTimeZone;
    }
  });

  it("announces the statement loading state on the busy ledger region", async () => {
    const statement = deferred<Response>();
    vi.stubGlobal(
      "fetch",
      vi.fn(() => statement.promise),
    );
    render(<SettlementWorkspace session={session} quotes={[]} />);

    const ledger = screen
      .getByRole("heading", { name: "Signed settlement statement" })
      .closest("section");
    expect(ledger).toHaveAttribute("aria-busy", "true");
    expect(
      screen.getByText("Loading settlement statement…"),
    ).toBeInTheDocument();

    statement.resolve(
      await json({ entries: [], page: 0, size: 50, hasNext: false }),
    );
    await act(async () => {
      await Promise.resolve();
    });
    expect(ledger).toHaveAttribute("aria-busy", "false");
  });

  it("keeps filters in the URL and renders settlement and reversal as separate signed rows", async () => {
    const fetchMock = vi.fn((_url: string) =>
      json({
        entries: [
          {
            entryId: "e1",
            entryType: "SETTLEMENT",
            signedAmount: "975.61",
            settlementCurrency: "BRL",
            settlementId: "S975",
            effectiveAt: "2030-01-15T12:00:00Z",
          },
          {
            entryId: "e2",
            entryType: "REVERSAL",
            signedAmount: "-975.61",
            settlementCurrency: "BRL",
            settlementId: "S975",
            effectiveAt: "2030-01-16T12:00:00Z",
          },
        ],
        page: 0,
        size: 50,
        hasNext: false,
      }),
    );
    vi.stubGlobal("fetch", fetchMock);
    render(<SettlementWorkspace session={session} quotes={[]} />);
    await screen.findByText("SETTLEMENT");
    vi.useFakeTimers();
    fireEvent.change(screen.getByLabelText("Ledger settlement currency"), {
      target: { value: "brl" },
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(300);
    });
    expect(window.location.search).toContain("settlementCurrency=BRL");
    expect(screen.getByText("REVERSAL")).toBeInTheDocument();
    expect(screen.getByText("-975.61")).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "S975" })).toHaveLength(2);
  });

  it("restores all URL-backed filters on browser navigation", async () => {
    const fetchMock = vi.fn((_url: string) =>
      json({ entries: [], page: 0, size: 25, hasNext: false }),
    );
    vi.stubGlobal("fetch", fetchMock);
    window.history.replaceState(
      null,
      "",
      "/?assignorId=a1&assetCurrency=USD&from=2030-01-01T00%3A00%3A00.000Z&size=25",
    );
    render(<SettlementWorkspace session={session} quotes={[]} />);
    expect(screen.getByLabelText("Ledger assignor ID")).toHaveValue("a1");
    expect(screen.getByLabelText("Ledger asset currency")).toHaveValue("USD");
    expect(screen.getByLabelText("Page size")).toHaveValue("25");
    window.history.pushState(
      null,
      "",
      "/?productType=MERCANTILE_INVOICE&page=2",
    );
    window.dispatchEvent(new PopStateEvent("popstate"));
    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.getByLabelText("Ledger product")).toHaveValue(
      "MERCANTILE_INVOICE",
    );
    expect(screen.getByLabelText("Ledger assignor ID")).toHaveValue("");
    expect(fetchMock.mock.calls.at(-1)?.[0]).toContain(
      "productType=MERCANTILE_INVOICE",
    );
  });

  it("removes rows from the prior query while a replacement loads and after it fails", async () => {
    const replacement = deferred<Response>();
    let statementCalls = 0;
    const fetchMock = vi.fn((url: string) => {
      if (!url.includes("settlement-statements"))
        throw new Error(`Unexpected request: ${url}`);
      statementCalls += 1;
      if (statementCalls === 1)
        return json({
          entries: [
            {
              entryId: "old-entry",
              entryType: "OLD",
              signedAmount: "50.00",
              settlementCurrency: "USD",
              settlementId: "OLD-SETTLEMENT",
              effectiveAt: "2030-01-15T12:00:00Z",
            },
          ],
          page: 0,
          size: 50,
          hasNext: false,
        });
      return replacement.promise;
    });
    vi.stubGlobal("fetch", fetchMock);
    window.history.replaceState(null, "", "/?productType=OLD");
    render(<SettlementWorkspace session={session} quotes={[]} />);
    await screen.findByText("OLD");

    fireEvent.change(screen.getByLabelText("Ledger product"), {
      target: { value: "NEW" },
    });
    expect(screen.queryByText("OLD")).toBeNull();
    replacement.resolve(problem("REPORT_FAILED", 500));
    await screen.findByText("REPORT_FAILED");
    expect(screen.queryByText("OLD")).toBeNull();
  });

  it("keeps the newest URL-backed ledger response when an older request resolves last", async () => {
    const older = deferred<Response>();
    const newer = deferred<Response>();
    let statementCalls = 0;
    const fetchMock = vi.fn((url: string, _init?: RequestInit) => {
      if (!url.includes("settlement-statements"))
        throw new Error(`Unexpected request: ${url}`);
      statementCalls += 1;
      return statementCalls === 1 ? older.promise : newer.promise;
    });
    vi.stubGlobal("fetch", fetchMock);
    window.history.replaceState(null, "", "/?productType=OLD&page=3&size=25");
    render(<SettlementWorkspace session={session} quotes={[]} />);

    fireEvent.change(screen.getByLabelText("Ledger product"), {
      target: { value: "NEW" },
    });
    await act(async () => {
      await Promise.resolve();
    });
    expect(fetchMock.mock.calls[0][1]?.signal?.aborted).toBe(true);
    expect(window.location.search).toContain("productType=NEW");
    expect(window.location.search).toContain("page=0");

    newer.resolve(
      json({
        entries: [
          {
            entryId: "new-entry",
            entryType: "NEW",
            signedAmount: "100.00",
            settlementCurrency: "BRL",
            settlementId: "NEW-SETTLEMENT",
            effectiveAt: "2030-01-16T12:00:00Z",
          },
        ],
        page: 0,
        size: 25,
        hasNext: false,
      }),
    );
    await screen.findByText("NEW");
    older.resolve(
      json({
        entries: [
          {
            entryId: "old-entry",
            entryType: "OLD",
            signedAmount: "50.00",
            settlementCurrency: "USD",
            settlementId: "OLD-SETTLEMENT",
            effectiveAt: "2030-01-15T12:00:00Z",
          },
        ],
        page: 3,
        size: 25,
        hasNext: false,
      }),
    );
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(screen.getByText("NEW")).toBeInTheDocument();
    expect(screen.queryByText("OLD")).toBeNull();
  });

  it("keeps the newest hash settlement detail when an older request resolves last", async () => {
    const older = deferred<Response>();
    const newer = deferred<Response>();
    let detailCalls = 0;
    const fetchMock = vi.fn((url: string, _init?: RequestInit) => {
      if (!url.includes("/settlements/"))
        throw new Error(`Unexpected request: ${url}`);
      detailCalls += 1;
      return detailCalls === 1 ? older.promise : newer.promise;
    });
    vi.stubGlobal("fetch", fetchMock);
    window.history.replaceState(null, "", "/#settlement-OLD-ID");
    render(
      <SettlementWorkspace session={session} quotes={[]} showLedger={false} />,
    );

    window.history.pushState(null, "", "/#settlement-NEW-ID");
    window.dispatchEvent(new Event("hashchange"));
    await act(async () => {
      await Promise.resolve();
    });
    expect(fetchMock.mock.calls[0][1]?.signal?.aborted).toBe(true);

    newer.resolve(
      json({
        settlementId: "NEW-ID",
        status: "COMPLETED",
        items: [],
        settlementCurrency: "BRL",
        totalAmount: "200.00",
      }),
    );
    await screen.findByText("NEW-ID");
    older.resolve(
      json({
        settlementId: "OLD-ID",
        status: "COMPLETED",
        items: [],
        settlementCurrency: "USD",
        totalAmount: "100.00",
      }),
    );
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(screen.getByText("NEW-ID")).toBeInTheDocument();
    expect(screen.queryByText("OLD-ID")).toBeNull();
  });
  it.each([
    [401, "Your session has expired. Sign in again."],
    [403, "Your role is not allowed to perform this action."],
  ])(
    "explains preview authorization failure %i without retaining a preview",
    async (status, expectedMessage) => {
      const dispatchEvent = vi.spyOn(window, "dispatchEvent");
      vi.stubGlobal(
        "fetch",
        vi.fn((url: string) => {
          if (url.includes("settlement-statements"))
            return json({ entries: [], page: 0, size: 50, hasNext: false });
          if (url.includes("settlement-previews"))
            return problem("PREVIEW_DENIED", status);
          throw new Error(`Unexpected ${url}`);
        }),
      );
      render(<SettlementWorkspace session={session} quotes={[quote]} />);

      fireEvent.click(screen.getByRole("checkbox"));
      fireEvent.click(
        screen.getByRole("button", { name: "Request server preview" }),
      );

      expect(await screen.findByText(expectedMessage)).toBeInTheDocument();
      expect(screen.queryByLabelText("Server settlement preview")).toBeNull();
      expect(
        dispatchEvent.mock.calls.filter(
          ([event]) => event.type === "srm:session-expired",
        ),
      ).toHaveLength(status === 401 ? 1 : 0);
    },
  );

  it("discards an invalid persisted intent instead of selecting another actor's quotes", () => {
    localStorage.setItem("srm-settlement-intent", "{");
    vi.stubGlobal(
      "fetch",
      vi.fn(() => json({ entries: [], page: 0, size: 50, hasNext: false })),
    );

    render(<SettlementWorkspace session={session} quotes={[quote]} />);

    expect(screen.getByRole("checkbox")).not.toBeChecked();
    expect(
      screen.queryByText(/saved settlement intent was restored/i),
    ).not.toBeInTheDocument();
  });

  it("cancels a retryable intent explicitly before another selection can be settled", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.includes("settlement-statements"))
          return json({ entries: [], page: 0, size: 50, hasNext: false });
        if (url.includes("settlement-previews")) return json(preview);
        if (url.includes("settlements"))
          return Promise.reject(new TypeError("Network lost"));
        throw new Error(`Unexpected ${url}`);
      }),
    );
    render(<SettlementWorkspace session={session} quotes={[quote, quoteB]} />);

    fireEvent.click(screen.getAllByRole("checkbox")[0]);
    fireEvent.click(
      screen.getByRole("button", { name: "Request server preview" }),
    );
    await screen.findByRole("button", { name: "Confirm settlement" });
    fireEvent.click(screen.getByRole("button", { name: "Confirm settlement" }));
    await screen.findByText(/Retry uses the same settlement intent/);
    fireEvent.click(screen.getByRole("button", { name: "Cancel intent" }));

    expect(
      screen.getByText("Settlement intent cancelled."),
    ).toBeInTheDocument();
    expect(localStorage.getItem("srm-settlement-intent")).toBeNull();
  });

  it("updates all ledger filters and navigates both pagination directions", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() =>
        json({
          entries: [
            {
              entryId: "entry-1",
              entryType: "SETTLEMENT",
              signedAmount: "100.00",
              settlementCurrency: "BRL",
              settlementId: "S-1",
              effectiveAt: "2030-01-15T12:00:00Z",
            },
          ],
          page: 1,
          size: 50,
          hasNext: true,
        }),
      ),
    );
    render(<SettlementWorkspace session={session} quotes={[]} />);
    await screen.findByText("SETTLEMENT");

    fireEvent.change(screen.getByLabelText("Ledger product"), {
      target: { value: "INVOICE" },
    });
    fireEvent.change(screen.getByLabelText("Ledger product"), {
      target: { value: "" },
    });
    fireEvent.change(screen.getByLabelText("From filter"), {
      target: { value: "2030-01-01T00:00" },
    });
    fireEvent.change(screen.getByLabelText("To filter"), {
      target: { value: "2030-01-31T23:59" },
    });
    fireEvent.change(screen.getByLabelText("Ledger assignor ID"), {
      target: { value: "assignor-1" },
    });
    fireEvent.change(screen.getByLabelText("Ledger asset currency"), {
      target: { value: "usd" },
    });
    fireEvent.change(screen.getByLabelText("Page size"), {
      target: { value: "100" },
    });
    fireEvent.click(await screen.findByRole("button", { name: "Previous" }));
    fireEvent.click(await screen.findByRole("button", { name: "Next" }));

    expect(window.location.search).toContain("assignorId=assignor-1");
    expect(window.location.search).toContain("assetCurrency=USD");
    expect(window.location.search).toContain("size=100");
    expect(window.location.search).toContain("page=2");
  });

  it("routes settlement-detail authorization failure through the session module", async () => {
    const dispatchEvent = vi.spyOn(window, "dispatchEvent");
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.includes("/settlements/detail-1"))
          return problem("DETAIL_DENIED", 401);
        throw new Error(`Unexpected ${url}`);
      }),
    );
    window.history.replaceState(null, "", "/#settlement-detail-1");

    render(
      <SettlementWorkspace session={session} quotes={[]} showLedger={false} />,
    );

    expect(
      await screen.findByText("Your session has expired. Sign in again."),
    ).toBeInTheDocument();
    expect(
      dispatchEvent.mock.calls.filter(
        ([event]) => event.type === "srm:session-expired",
      ),
    ).toHaveLength(1);
  });
  it("routes settlement authorization failure through the session module", async () => {
    const dispatchEvent = vi.spyOn(window, "dispatchEvent");
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.includes("settlement-statements"))
          return json({ entries: [], page: 0, size: 50, hasNext: false });
        if (url.includes("settlement-previews")) return json(preview);
        if (url.includes("settlements")) return problem("SETTLE_DENIED", 401);
        throw new Error(`Unexpected ${url}`);
      }),
    );
    render(<SettlementWorkspace session={session} quotes={[quote]} />);

    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(
      screen.getByRole("button", { name: "Request server preview" }),
    );
    await screen.findByRole("button", { name: "Confirm settlement" });
    fireEvent.click(screen.getByRole("button", { name: "Confirm settlement" }));

    await screen.findByText(/Retry uses the same settlement intent/);
    expect(
      dispatchEvent.mock.calls.filter(
        ([event]) => event.type === "srm:session-expired",
      ),
    ).toHaveLength(1);
  });
  it("silently discards an aborted preview request", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string, init?: RequestInit) => {
        if (url.includes("settlement-statements"))
          return json({ entries: [], page: 0, size: 50, hasNext: false });
        if (url.includes("settlement-previews"))
          return new Promise<Response>((_resolve, reject) => {
            init?.signal?.addEventListener("abort", () =>
              reject(new DOMException("Aborted", "AbortError")),
            );
          });
        throw new Error(`Unexpected ${url}`);
      }),
    );
    render(<SettlementWorkspace session={session} quotes={[quote]} />);

    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(
      screen.getByRole("button", { name: "Request server preview" }),
    );
    fireEvent.click(screen.getByRole("checkbox"));
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(
      screen.queryByText("Could not obtain a settlement preview."),
    ).toBeNull();
  });
  it("falls back to a generated settlement key when random UUID support is unavailable", async () => {
    vi.stubGlobal("crypto", {});
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.includes("settlement-statements"))
          return json({ entries: [], page: 0, size: 50, hasNext: false });
        if (url.includes("settlement-previews")) return json(preview);
        if (url.includes("settlements"))
          return Promise.reject(new TypeError("Network lost"));
        throw new Error(`Unexpected ${url}`);
      }),
    );
    render(<SettlementWorkspace session={session} quotes={[quote]} />);
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(
      screen.getByRole("button", { name: "Request server preview" }),
    );
    await screen.findByRole("button", { name: "Confirm settlement" });
    fireEvent.click(screen.getByRole("button", { name: "Confirm settlement" }));
    await screen.findByText(/Retry uses the same settlement intent/);

    expect(localStorage.getItem("srm-settlement-intent")).toContain(
      "settlement-",
    );
  });

  it("clears the preview expiry timer on unmount", async () => {
    const clearTimeout = vi.spyOn(window, "clearTimeout");
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.includes("settlement-statements"))
          return json({ entries: [], page: 0, size: 50, hasNext: false });
        if (url.includes("settlement-previews")) return json(preview);
        throw new Error(`Unexpected ${url}`);
      }),
    );
    const view = render(
      <SettlementWorkspace session={session} quotes={[quote]} />,
    );
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(
      screen.getByRole("button", { name: "Request server preview" }),
    );
    await screen.findByRole("button", { name: "Confirm settlement" });
    view.unmount();

    expect(clearTimeout).toHaveBeenCalled();
  });

  it("renders detail authorization denial without expiring a valid session", async () => {
    const onExpired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.includes("/settlements/detail-403"))
          return problem("DETAIL_DENIED", 403);
        throw new Error(`Unexpected ${url}`);
      }),
    );
    window.history.replaceState(null, "", "/#settlement-detail-403");
    render(
      <SettlementWorkspace session={session} quotes={[]} showLedger={false} />,
    );

    expect(
      await screen.findByText(
        "Your role is not allowed to perform this action.",
      ),
    ).toBeInTheDocument();
    window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
    expect(onExpired).not.toHaveBeenCalled();
  });

  it("routes ledger authorization failure through the session module", async () => {
    const dispatchEvent = vi.spyOn(window, "dispatchEvent");
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.includes("settlement-statements"))
          return problem("LEDGER_DENIED", 401);
        throw new Error(`Unexpected ${url}`);
      }),
    );
    render(<SettlementWorkspace session={session} quotes={[]} />);

    expect(
      await screen.findByText("Your session has expired. Sign in again."),
    ).toBeInTheDocument();
    expect(
      dispatchEvent.mock.calls.filter(
        ([event]) => event.type === "srm:session-expired",
      ),
    ).toHaveLength(1);
  });
});
