import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi,
  type Mock,
} from "vitest";
import App, { SIMULATION_DEBOUNCE_MS } from "./App";

const simulation = (amount: string) => ({
  faceAmount: "1000.00",
  faceCurrency: "BRL",
  settlementCurrency: "BRL",
  productType: "MERCANTILE_INVOICE",
  dueDate: "2030-02-14",
  baseRate: "0.010",
  spread: "0.015",
  strategyCode: "INVOICE",
  dayCountConvention: "ACTUAL_DAYS_30_MONTH",
  termInMonths: "1.0000000000",
  discountedAmount: amount,
  fxBaseCurrency: "BRL",
  fxQuoteCurrency: "BRL",
  fxRate: "1",
  fxSource: "IDENTITY",
  fxObservedAt: "2030-01-15T12:00:00Z",
  settlementAmount: amount,
  pricedAt: "2030-01-15T12:00:00Z",
});

const crossCurrencySimulation = {
  ...simulation("193.24"),
  faceCurrency: "BRL",
  settlementCurrency: "USD",
  fxBaseCurrency: "BRL",
  fxQuoteCurrency: "USD",
  fxRate: "0.2000000000",
  fxSource: "MANUAL",
  settlementAmount: "193.24",
};

const pricing = (amount: string) => ({
  faceAmount: "1000.00",
  faceCurrency: "BRL",
  settlementCurrency: "BRL",
  baseRate: "0.010",
  spread: "0.015",
  strategyCode: "INVOICE",
  dayCountConvention: "ACTUAL_DAYS_30_MONTH",
  termInMonths: "1.0000000000",
  discountedAmount: amount,
  fxBaseCurrency: "BRL",
  fxQuoteCurrency: "BRL",
  fxRate: "1",
  fxSource: "IDENTITY",
  fxObservedAt: "2030-01-15T12:00:00Z",
  settlementAmount: amount,
  pricedAt: "2030-01-15T12:00:00Z",
});

function response(body: unknown, status = 200) {
  return Promise.resolve(
    new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    }),
  );
}

const staleFx = response(
  { status: 409, code: "FX_RATE_STALE", detail: "Selected FX rate is stale." },
  409,
);

function stubFetch(fetchMock: Mock) {
  vi.stubGlobal(
    "fetch",
    vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input).includes("/settlement-statements"))
        return response({ entries: [], page: 0, size: 50, hasNext: false });
      return fetchMock(input, init);
    }),
  );
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

const quote = {
  id: "quote-1",
  receivableId: "receivable-1",
  productType: "MERCANTILE_INVOICE",
  dueDate: "2030-02-14",
  pricing: pricing("966.18"),
  expiresAt: "2030-01-15T12:05:00Z",
  status: "ACTIVE",
};

const ASSIGNOR_A = "11111111-1111-1111-1111-111111111111";
const ASSIGNOR_B = "22222222-2222-2222-2222-222222222222";
async function signIn() {
  fireEvent.change(screen.getByLabelText("Password"), {
    target: { value: "test-password" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
  expect(
    screen.getByRole("heading", { name: "Live receivable pricing" }),
  ).toBeInTheDocument();
}

describe("UI-SIM-002 and UI-SIM-005 authoritative pricing workflow", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.useFakeTimers();
  });
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("debounces a product change and renders only the server result", async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() =>
        response({ accessToken: "token", expiresIn: 900 }),
      )
      .mockImplementationOnce(() =>
        response({ email: "operator@srm.local", roles: ["OPERATOR"] }),
      )
      .mockImplementationOnce(() => response(simulation("966.18")));
    stubFetch(fetchMock);
    render(<App />);
    await signIn();
    fireEvent.change(screen.getByLabelText("Product"), {
      target: { value: "POST_DATED_CHEQUE" },
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(SIMULATION_DEBOUNCE_MS - 1);
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
    });
    expect(screen.getAllByRole("status")[0]).toHaveTextContent(
      "Prices are calculated",
    );
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[2][0]).toContain("/pricing-simulations");
    expect(screen.getAllByText("966.18")).not.toHaveLength(0);
    expect(
      fetchMock.mock.calls.some(
        ([url]) =>
          String(url).includes("pricing-quotes") ||
          String(url).includes("settlements"),
      ),
    ).toBe(false);
  });

  it("renders server-provided cross-currency FX metadata", async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() =>
        response({ accessToken: "token", expiresIn: 900 }),
      )
      .mockImplementationOnce(() =>
        response({ email: "operator@srm.local", roles: ["OPERATOR"] }),
      )
      .mockImplementationOnce(() => response(crossCurrencySimulation));
    stubFetch(fetchMock);
    render(<App />);
    await signIn();

    fireEvent.change(screen.getByLabelText("Product"), {
      target: { value: "POST_DATED_CHEQUE" },
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(SIMULATION_DEBOUNCE_MS);
    });

    const simulationRegion = screen
      .getByRole("heading", { name: "Server simulation" })
      .closest("section");
    expect(simulationRegion).not.toBeNull();
    expect(simulationRegion!).toHaveTextContent("BRL/USD");
    expect(simulationRegion!).toHaveTextContent("0.2000000000");
    expect(simulationRegion!).toHaveTextContent("MANUAL");
  });

  it("never lets an old response overwrite the newest result", async () => {
    let resolveOld!: (value: Response) => void;
    const old = new Promise<Response>((resolve) => {
      resolveOld = resolve;
    });
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() =>
        response({ accessToken: "token", expiresIn: 900 }),
      )
      .mockImplementationOnce(() =>
        response({ email: "operator@srm.local", roles: ["OPERATOR"] }),
      )
      .mockImplementationOnce(() => old)
      .mockImplementationOnce(() => response(simulation("966.18")));
    stubFetch(fetchMock);
    render(<App />);
    await signIn();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(SIMULATION_DEBOUNCE_MS);
    });
    const oldSignal = (fetchMock.mock.calls[2][1] as RequestInit)
      .signal as AbortSignal;
    fireEvent.change(screen.getByLabelText("Product"), {
      target: { value: "POST_DATED_CHEQUE" },
    });
    expect(oldSignal.aborted).toBe(true);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(SIMULATION_DEBOUNCE_MS);
    });
    expect(screen.getAllByText("966.18")).not.toHaveLength(0);
    resolveOld(
      new Response(JSON.stringify(simulation("975.61")), { status: 200 }),
    );
    await Promise.resolve();
    await Promise.resolve();
    expect(screen.getAllByText("966.18")).not.toHaveLength(0);
    expect(screen.getByLabelText("Product")).toHaveValue("POST_DATED_CHEQUE");
  });

  it("returns to sign-in after an expired session", async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() =>
        response({ accessToken: "token", expiresIn: 0 }),
      )
      .mockImplementationOnce(() =>
        response({ email: "operator@srm.local", roles: ["OPERATOR"] }),
      );
    stubFetch(fetchMock);
    render(<App />);
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "test-password" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(
      screen.getByRole("heading", { name: "Operator sign in" }),
    ).toBeInTheDocument();
  });

  it.each([
    ["non-object", "not-a-session"],
    [
      "missing token",
      {
        expiresAt: Date.now() + 60_000,
        email: "operator@srm.local",
        roles: ["OPERATOR"],
      },
    ],
    [
      "unknown role",
      {
        accessToken: "token",
        expiresAt: Date.now() + 60_000,
        email: "operator@srm.local",
        roles: ["ROOT"],
      },
    ],
  ])("rejects a persisted session with %s", (_case, persisted) => {
    localStorage.setItem("srm-session", JSON.stringify(persisted));
    stubFetch(vi.fn());

    render(<App />);

    expect(
      screen.getByRole("heading", { name: "Operator sign in" }),
    ).toBeInTheDocument();
    expect(localStorage.getItem("srm-session")).toBeNull();
  });

  it.each(["ANALYST", "AUDITOR"])(
    "does not request simulations for a read-only %s session",
    async (role) => {
      const fetchMock = vi
        .fn()
        .mockImplementationOnce(() =>
          response({ accessToken: "token", expiresIn: 900 }),
        )
        .mockImplementationOnce(() =>
          response({
            email: `${role.toLowerCase()}@srm.local`,
            roles: [role],
          }),
        );
      stubFetch(fetchMock);

      render(<App />);
      await signIn();
      await act(async () => {
        await vi.advanceTimersByTimeAsync(SIMULATION_DEBOUNCE_MS);
      });

      expect(
        fetchMock.mock.calls.some(([url]) =>
          String(url).includes("/pricing-simulations"),
        ),
      ).toBe(false);
      expect(screen.queryByText("Requesting authoritative price…")).toBeNull();
    },
  );

  it.each(["OPERATOR", "ANALYST", "ADMIN", "AUDITOR"])(
    "mounts the ledger and makes one statement request for %s",
    async (role) => {
      localStorage.setItem(
        "srm-session",
        JSON.stringify({
          accessToken: "token",
          expiresAt: Date.now() + 60_000,
          email: `${role.toLowerCase()}@srm.local`,
          roles: [role],
        }),
      );
      const fetchMock = vi.fn((url: string) => {
        if (url.includes("settlement-statements"))
          return response({ entries: [], page: 0, size: 50, hasNext: false });
        throw new Error(`Unexpected request: ${url}`);
      });
      vi.stubGlobal("fetch", fetchMock);

      render(<App />);
      expect(
        screen.getByRole("heading", { name: "Signed settlement statement" }),
      ).toBeInTheDocument();
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      expect(
        fetchMock.mock.calls.filter(([url]) =>
          String(url).includes("settlement-statements"),
        ),
      ).toHaveLength(1);
    },
  );

  it("keeps the last successful simulation visibly stale when its replacement fails", async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() =>
        response({ accessToken: "token", expiresIn: 900 }),
      )
      .mockImplementationOnce(() =>
        response({ email: "operator@srm.local", roles: ["OPERATOR"] }),
      )
      .mockImplementationOnce(() => response(simulation("975.61")))
      .mockImplementationOnce(() =>
        response({ detail: "Pricing service unavailable." }, 503),
      );
    stubFetch(fetchMock);

    render(<App />);
    await signIn();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(SIMULATION_DEBOUNCE_MS);
    });
    fireEvent.change(screen.getByLabelText("Product"), {
      target: { value: "POST_DATED_CHEQUE" },
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(SIMULATION_DEBOUNCE_MS);
    });

    expect(screen.getByLabelText("Stale server simulation")).toHaveClass(
      "stale-result",
    );
    expect(screen.getByLabelText("Stale server simulation")).toHaveTextContent(
      "975.61",
    );
    expect(screen.getAllByRole("status")[0]).toHaveTextContent(
      "Latest server refresh failed — displayed values are stale.",
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Pricing service unavailable.",
    );
  });

  it("renders a stale FX server detail in the focused alert", async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() =>
        response({ accessToken: "token", expiresIn: 900 }),
      )
      .mockImplementationOnce(() =>
        response({ email: "operator@srm.local", roles: ["OPERATOR"] }),
      )
      .mockImplementationOnce(() => response(simulation("193.24")))
      .mockImplementationOnce(() => staleFx);
    stubFetch(fetchMock);

    render(<App />);
    await signIn();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(SIMULATION_DEBOUNCE_MS);
    });
    fireEvent.change(screen.getByLabelText("Product"), {
      target: { value: "POST_DATED_CHEQUE" },
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(SIMULATION_DEBOUNCE_MS);
    });

    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("Selected FX rate is stale.");
    expect(alert).toHaveFocus();
  });

  it("invalidates a registered receivable when any authoritative input changes", async () => {
    let receivableNumber = 0;
    const fetchMock = vi.fn((url: string) => {
      if (url.endsWith("/auth/login"))
        return response({ accessToken: "token", expiresIn: 900 });
      if (url.endsWith("/users/me"))
        return response({ email: "operator@srm.local", roles: ["OPERATOR"] });
      if (url.endsWith("/pricing-simulations"))
        return response(simulation("975.61"));
      if (url.endsWith("/receivables"))
        return response({ id: `receivable-${++receivableNumber}` });
      throw new Error(`Unexpected request: ${url}`);
    });
    stubFetch(fetchMock);

    render(<App />);
    await signIn();
    fireEvent.change(screen.getByLabelText(/Assignor ID/), {
      target: { value: ASSIGNOR_A },
    });

    const changes = [
      ["Product", "POST_DATED_CHEQUE"],
      ["Face amount", "2000.00"],
      ["Face currency", "USD"],
      ["Settlement currency", "USD"],
      ["Issue date", "2029-12-01"],
      ["Due date", "2030-03-01"],
      ["Assignor ID (for explicit registration)", ASSIGNOR_B],
    ] as const;

    for (const [label, value] of changes) {
      fireEvent.click(
        screen.getByRole("button", { name: "Register receivable" }),
      );
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });
      expect(
        screen.getByRole("button", { name: "Create quote" }),
      ).toBeEnabled();

      fireEvent.change(screen.getByLabelText(label), {
        target: { value },
      });
      expect(
        screen.getByRole("button", { name: "Create quote" }),
      ).toBeDisabled();
    }
  });

  it("does not install a receivable response for inputs that have since changed", async () => {
    const registration = deferred<Response>();
    const fetchMock = vi.fn((url: string) => {
      if (url.endsWith("/auth/login"))
        return response({ accessToken: "token", expiresIn: 900 });
      if (url.endsWith("/users/me"))
        return response({ email: "operator@srm.local", roles: ["OPERATOR"] });
      if (url.endsWith("/pricing-simulations"))
        return response(simulation("975.61"));
      if (url.endsWith("/receivables")) return registration.promise;
      throw new Error(`Unexpected request: ${url}`);
    });
    stubFetch(fetchMock);

    render(<App />);
    await signIn();
    fireEvent.change(screen.getByLabelText(/Assignor ID/), {
      target: { value: ASSIGNOR_A },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Register receivable" }),
    );
    fireEvent.change(screen.getByLabelText("Face amount"), {
      target: { value: "2000.00" },
    });
    registration.resolve(
      new Response(JSON.stringify({ id: "receivable-1" }), { status: 200 }),
    );
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(screen.getByRole("button", { name: "Create quote" })).toBeDisabled();
  });

  it("prevents duplicate receivable and quote mutations while each is pending", async () => {
    const registration = deferred<Response>();
    const quotation = deferred<Response>();
    const fetchMock = vi.fn((url: string) => {
      if (url.endsWith("/auth/login"))
        return response({ accessToken: "token", expiresIn: 900 });
      if (url.endsWith("/users/me"))
        return response({ email: "operator@srm.local", roles: ["OPERATOR"] });
      if (url.endsWith("/pricing-simulations"))
        return response(simulation("975.61"));
      if (url.endsWith("/receivables")) return registration.promise;
      if (url.endsWith("/pricing-quotes")) return quotation.promise;
      throw new Error(`Unexpected request: ${url}`);
    });
    stubFetch(fetchMock);

    render(<App />);
    await signIn();
    fireEvent.change(screen.getByLabelText(/Assignor ID/), {
      target: { value: ASSIGNOR_A },
    });
    const register = screen.getByRole("button", {
      name: "Register receivable",
    });
    fireEvent.click(register);
    fireEvent.click(register);
    expect(
      fetchMock.mock.calls.filter(([url]) =>
        String(url).endsWith("/receivables"),
      ),
    ).toHaveLength(1);
    expect(screen.getByRole("button", { name: "Registering…" })).toBeDisabled();

    registration.resolve(
      new Response(JSON.stringify({ id: "receivable-1" }), { status: 200 }),
    );
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    const createQuote = screen.getByRole("button", { name: "Create quote" });
    fireEvent.click(createQuote);
    fireEvent.click(createQuote);
    expect(
      fetchMock.mock.calls.filter(([url]) =>
        String(url).endsWith("/pricing-quotes"),
      ),
    ).toHaveLength(1);
    expect(
      screen.getByRole("button", { name: "Creating quote…" }),
    ).toBeDisabled();

    quotation.resolve(new Response(JSON.stringify(quote), { status: 200 }));
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
  });

  it("wire-shape regression: renders quote metadata from top-level fields", async () => {
    const fetchMock = vi.fn((url: string) => {
      if (url.endsWith("/auth/login"))
        return response({ accessToken: "token", expiresIn: 900 });
      if (url.endsWith("/users/me"))
        return response({ email: "operator@srm.local", roles: ["OPERATOR"] });
      if (url.endsWith("/pricing-simulations"))
        return response(simulation("975.61"));
      if (url.endsWith("/receivables")) return response({ id: "receivable-1" });
      if (url.endsWith("/pricing-quotes")) return response(quote);
      throw new Error(`Unexpected request: ${url}`);
    });
    stubFetch(fetchMock);

    render(<App />);
    await signIn();
    fireEvent.change(screen.getByLabelText(/Assignor ID/), {
      target: { value: ASSIGNOR_A },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Register receivable" }),
    );
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    fireEvent.click(screen.getByRole("button", { name: "Create quote" }));
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    const article = screen
      .getByRole("heading", { name: "Quote quote-1" })
      .closest("article")!;
    expect(article).toHaveTextContent("Product type");
    expect(article).toHaveTextContent("MERCANTILE_INVOICE");
    expect(article).toHaveTextContent("Due date");
    expect(article).toHaveTextContent("2030-02-14");
  });

  it("renders a complete quote breakdown with explicit expiry and status", async () => {
    const fetchMock = vi.fn((url: string) => {
      if (url.endsWith("/auth/login"))
        return response({ accessToken: "token", expiresIn: 900 });
      if (url.endsWith("/users/me"))
        return response({ email: "operator@srm.local", roles: ["OPERATOR"] });
      if (url.endsWith("/pricing-simulations"))
        return response(simulation("975.61"));
      if (url.endsWith("/receivables")) return response({ id: "receivable-1" });
      if (url.endsWith("/pricing-quotes")) return response(quote);
      throw new Error(`Unexpected request: ${url}`);
    });
    stubFetch(fetchMock);

    render(<App />);
    await signIn();
    fireEvent.change(screen.getByLabelText(/Assignor ID/), {
      target: { value: ASSIGNOR_A },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Register receivable" }),
    );
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    fireEvent.click(screen.getByRole("button", { name: "Create quote" }));
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    const breakdown = screen
      .getByRole("heading", { name: "Quote quote-1" })
      .closest("article");
    expect(breakdown).not.toBeNull();
    expect(within(breakdown!).getByText("MERCANTILE_INVOICE")).toBeVisible();
    expect(within(breakdown!).getByText("2030-02-14")).toBeVisible();
    for (const term of [
      "Face amount",
      "Base rate",
      "Spread",
      "Strategy",
      "Day count",
      "Term in months",
      "Discounted amount",
      "FX pair",
      "FX rate",
      "FX source",
      "FX observed at",
      "Settlement amount",
      "Priced at",
      "Expires at",
      "Status",
    ]) {
      expect(within(breakdown!).getByText(term)).toBeVisible();
    }
    expect(within(breakdown!).getByText("2030-01-15T12:05:00Z")).toBeVisible();
    expect(within(breakdown!).getByText("ACTIVE")).toBeVisible();
  });

  it("associates validation errors only with their invalid fields", async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() =>
        response({ accessToken: "token", expiresIn: 900 }),
      )
      .mockImplementationOnce(() =>
        response({ email: "operator@srm.local", roles: ["OPERATOR"] }),
      );
    stubFetch(fetchMock);

    render(<App />);
    await signIn();
    fireEvent.change(screen.getByLabelText("Face amount"), {
      target: { value: "-1" },
    });
    fireEvent.change(screen.getByLabelText("Face currency"), {
      target: { value: "US" },
    });

    const amount = screen.getByLabelText("Face amount");
    const currency = screen.getByLabelText("Face currency");
    const settlementCurrency = screen.getByLabelText("Settlement currency");
    expect(amount).toHaveAttribute("aria-describedby", "faceAmount-error");
    expect(currency).toHaveAttribute("aria-describedby", "faceCurrency-error");
    expect(settlementCurrency).toHaveAttribute("aria-invalid", "false");
    expect(
      screen.getByText(
        "Enter a positive amount with up to four decimal places.",
      ),
    ).toHaveAttribute("id", "faceAmount-error");
  });

  it("validates registration-only UUID and date ordering without stopping simulation", async () => {
    const fetchMock = vi.fn((url: string) => {
      if (url.endsWith("/auth/login"))
        return response({ accessToken: "token", expiresIn: 900 });
      if (url.endsWith("/users/me"))
        return response({ email: "operator@srm.local", roles: ["OPERATOR"] });
      if (url.endsWith("/pricing-simulations"))
        return response(simulation("975.61"));
      throw new Error(`Unexpected request: ${url}`);
    });
    stubFetch(fetchMock);

    render(<App />);
    await signIn();
    fireEvent.change(screen.getByLabelText(/Assignor ID/), {
      target: { value: "not-a-uuid" },
    });
    fireEvent.change(screen.getByLabelText("Issue date"), {
      target: { value: "2030-02-15" },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Register receivable" }),
    );
    await act(async () => {
      await vi.advanceTimersByTimeAsync(SIMULATION_DEBOUNCE_MS);
    });

    expect(screen.getByLabelText(/Assignor ID/)).toHaveAttribute(
      "aria-describedby",
      "assignor-help assignorId-error",
    );
    expect(screen.getByText("Enter a valid assignor UUID.")).toHaveAttribute(
      "id",
      "assignorId-error",
    );
    expect(screen.getByLabelText("Due date")).toHaveAttribute(
      "aria-describedby",
      "dueDate-error",
    );
    expect(
      fetchMock.mock.calls.some(([url]) =>
        String(url).endsWith("/pricing-simulations"),
      ),
    ).toBe(true);
    expect(
      fetchMock.mock.calls.some(([url]) =>
        String(url).endsWith("/receivables"),
      ),
    ).toBe(false);
  });

  it("keeps an assignor error when an unrelated field changes", async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() =>
        response({ accessToken: "token", expiresIn: 900 }),
      )
      .mockImplementationOnce(() =>
        response({ email: "operator@srm.local", roles: ["OPERATOR"] }),
      );
    stubFetch(fetchMock);

    render(<App />);
    await signIn();
    fireEvent.change(screen.getByLabelText(/Assignor ID/), {
      target: { value: "not-a-uuid" },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Register receivable" }),
    );
    fireEvent.change(screen.getByLabelText("Product"), {
      target: { value: "POST_DATED_CHEQUE" },
    });

    expect(screen.getByText("Enter a valid assignor UUID.")).toBeVisible();
    expect(screen.getByLabelText(/Assignor ID/)).toHaveAttribute(
      "aria-invalid",
      "true",
    );
  });

  it.each([
    ["successful", new Response(JSON.stringify(quote), { status: 200 }), true],
    [
      "failed",
      new Response(JSON.stringify({ detail: "Quote service failed." }), {
        status: 503,
        headers: { "Content-Type": "application/json" },
      }),
      false,
    ],
  ] as const)(
    "does not install %s quote feedback after its form chain changes",
    async (_outcome, quoteResponse, preservesHistory) => {
      const quotation = deferred<Response>();
      const fetchMock = vi.fn((url: string) => {
        if (url.endsWith("/auth/login"))
          return response({ accessToken: "token", expiresIn: 900 });
        if (url.endsWith("/users/me"))
          return response({
            email: "operator@srm.local",
            roles: ["OPERATOR"],
          });
        if (url.endsWith("/pricing-simulations"))
          return response(simulation("975.61"));
        if (url.endsWith("/receivables"))
          return response({ id: "receivable-1" });
        if (url.endsWith("/pricing-quotes")) return quotation.promise;
        throw new Error(`Unexpected request: ${url}`);
      });
      stubFetch(fetchMock);

      render(<App />);
      await signIn();
      fireEvent.change(screen.getByLabelText(/Assignor ID/), {
        target: { value: ASSIGNOR_A },
      });
      fireEvent.click(
        screen.getByRole("button", { name: "Register receivable" }),
      );
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });
      fireEvent.click(screen.getByRole("button", { name: "Create quote" }));
      fireEvent.change(screen.getByLabelText("Face amount"), {
        target: { value: "2000.00" },
      });
      quotation.resolve(quoteResponse);
      await act(async () => {
        await Promise.resolve();
        await Promise.resolve();
      });

      expect(
        screen.queryByText(/Quote quote-1 created|Quote service failed\./),
      ).toBeNull();
      expect(
        screen.queryByRole("heading", { name: "Quote quote-1" }) !== null,
      ).toBe(preservesHistory);
    },
  );

  it("renders successful feedback as status and focuses only mutation failures", async () => {
    const fetchMock = vi.fn((url: string) => {
      if (url.endsWith("/auth/login"))
        return response({ accessToken: "token", expiresIn: 900 });
      if (url.endsWith("/users/me"))
        return response({ email: "operator@srm.local", roles: ["OPERATOR"] });
      if (url.endsWith("/pricing-simulations"))
        return response(simulation("975.61"));
      if (url.endsWith("/receivables")) return response({ id: "receivable-1" });
      if (url.endsWith("/pricing-quotes"))
        return response({ detail: "Quote service failed." }, 503);
      throw new Error(`Unexpected request: ${url}`);
    });
    stubFetch(fetchMock);

    render(<App />);
    await signIn();
    fireEvent.change(screen.getByLabelText(/Assignor ID/), {
      target: { value: ASSIGNOR_A },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Register receivable" }),
    );
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    const success = screen.getByText(/Receivable receivable-1 registered/);
    expect(success).toHaveAttribute("role", "status");
    expect(success).toHaveClass("success");
    expect(success).not.toHaveClass("error");

    fireEvent.click(screen.getByRole("button", { name: "Create quote" }));
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    const failure = screen.getByRole("alert");
    expect(failure).toHaveTextContent("Quote service failed.");
    expect(failure).toHaveClass("error");
    expect(failure).toHaveFocus();
  });
  it("shows a generic login failure when the transport does not return a problem", async () => {
    stubFetch(
      vi.fn(() => Promise.reject(new TypeError("Network unavailable"))),
    );
    render(<App />);

    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "test-password" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(screen.getByRole("alert")).toHaveTextContent("Unable to sign in.");
  });

  it("expires the session when an authoritative simulation returns 401", async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() =>
        response({ accessToken: "token", expiresIn: 900 }),
      )
      .mockImplementationOnce(() =>
        response({ email: "operator@srm.local", roles: ["OPERATOR"] }),
      )
      .mockImplementationOnce(() => response({ detail: "Expired." }, 401));
    stubFetch(fetchMock);
    render(<App />);
    await signIn();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(SIMULATION_DEBOUNCE_MS);
    });

    expect(
      screen.getByRole("heading", { name: "Operator sign in" }),
    ).toBeInTheDocument();
  });

  it("renders a registration failure and focuses the operational alert", async () => {
    const fetchMock = vi.fn((url: string) => {
      if (url.endsWith("/auth/login"))
        return response({ accessToken: "token", expiresIn: 900 });
      if (url.endsWith("/users/me"))
        return response({ email: "operator@srm.local", roles: ["OPERATOR"] });
      if (url.endsWith("/receivables"))
        return response({ detail: "Registration unavailable." }, 503);
      throw new Error(`Unexpected request: ${url}`);
    });
    stubFetch(fetchMock);
    render(<App />);
    await signIn();

    fireEvent.change(screen.getByLabelText(/Assignor ID/), {
      target: { value: ASSIGNOR_A },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Register receivable" }),
    );
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("Registration unavailable.");
    expect(alert).toHaveFocus();
  });
  it("reports required pricing and registration fields independently", async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() =>
        response({ accessToken: "token", expiresIn: 900 }),
      )
      .mockImplementationOnce(() =>
        response({ email: "operator@srm.local", roles: ["OPERATOR"] }),
      );
    stubFetch(fetchMock);
    render(<App />);
    await signIn();

    fireEvent.change(screen.getByLabelText("Settlement currency"), {
      target: { value: "us" },
    });
    fireEvent.change(screen.getByLabelText("Issue date"), {
      target: { value: "" },
    });
    fireEvent.change(screen.getByLabelText("Due date"), {
      target: { value: "" },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Register receivable" }),
    );

    expect(
      screen.getAllByText(
        "Enter a three-letter uppercase settlement currency.",
      ),
    ).toHaveLength(2);
    expect(screen.getByText("Enter an issue date.")).toBeInTheDocument();
    expect(screen.getByText("Enter a due date.")).toBeInTheDocument();
  });

  it("expires a loaded session when the periodic expiry check reaches its deadline", async () => {
    localStorage.setItem(
      "srm-session",
      JSON.stringify({
        accessToken: "token",
        expiresAt: Date.now() + 1,
        email: "operator@srm.local",
        roles: ["OPERATOR"],
      }),
    );
    vi.stubGlobal(
      "fetch",
      vi.fn(() => response({ entries: [], page: 0, size: 50, hasNext: false })),
    );
    render(<App />);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });

    expect(
      screen.getByRole("heading", { name: "Operator sign in" }),
    ).toBeInTheDocument();
  });

  it("labels a native simulation failure as unavailable", async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() =>
        response({ accessToken: "token", expiresIn: 900 }),
      )
      .mockImplementationOnce(() =>
        response({ email: "operator@srm.local", roles: ["OPERATOR"] }),
      )
      .mockImplementationOnce(() =>
        Promise.reject(new TypeError("Network unavailable")),
      );
    stubFetch(fetchMock);
    render(<App />);
    await signIn();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(SIMULATION_DEBOUNCE_MS);
    });

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Simulation is unavailable.",
    );
  });
  it("renders the fallback when receivable registration loses transport", async () => {
    const fetchMock = vi.fn((url: string) => {
      if (url.endsWith("/auth/login"))
        return response({ accessToken: "token", expiresIn: 900 });
      if (url.endsWith("/users/me"))
        return response({ email: "operator@srm.local", roles: ["OPERATOR"] });
      if (url.endsWith("/receivables"))
        return Promise.reject(new TypeError("Network unavailable"));
      throw new Error(`Unexpected request: ${url}`);
    });
    stubFetch(fetchMock);
    render(<App />);
    await signIn();

    fireEvent.change(screen.getByLabelText(/Assignor ID/), {
      target: { value: ASSIGNOR_A },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Register receivable" }),
    );
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not register receivable.",
    );
  });

  it("renders the fallback when quote creation loses transport", async () => {
    const fetchMock = vi.fn((url: string) => {
      if (url.endsWith("/auth/login"))
        return response({ accessToken: "token", expiresIn: 900 });
      if (url.endsWith("/users/me"))
        return response({ email: "operator@srm.local", roles: ["OPERATOR"] });
      if (url.endsWith("/receivables")) return response({ id: "receivable-1" });
      if (url.endsWith("/pricing-quotes"))
        return Promise.reject(new TypeError("Network unavailable"));
      throw new Error(`Unexpected request: ${url}`);
    });
    stubFetch(fetchMock);
    render(<App />);
    await signIn();

    fireEvent.change(screen.getByLabelText(/Assignor ID/), {
      target: { value: ASSIGNOR_A },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Register receivable" }),
    );
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    fireEvent.click(screen.getByRole("button", { name: "Create quote" }));
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not create quote.",
    );
  });
  it("ignores a stale registration failure after authoritative inputs change", async () => {
    let rejectRegistration!: (cause: unknown) => void;
    const registration = new Promise<Response>((_resolve, reject) => {
      rejectRegistration = reject;
    });
    const fetchMock = vi.fn((url: string) => {
      if (url.endsWith("/auth/login"))
        return response({ accessToken: "token", expiresIn: 900 });
      if (url.endsWith("/users/me"))
        return response({ email: "operator@srm.local", roles: ["OPERATOR"] });
      if (url.endsWith("/receivables")) return registration;
      throw new Error(`Unexpected request: ${url}`);
    });
    stubFetch(fetchMock);
    render(<App />);
    await signIn();

    fireEvent.change(screen.getByLabelText(/Assignor ID/), {
      target: { value: ASSIGNOR_A },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Register receivable" }),
    );
    fireEvent.change(screen.getByLabelText("Face amount"), {
      target: { value: "2000.00" },
    });
    rejectRegistration(new TypeError("Network unavailable"));
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(screen.queryByRole("alert")).toBeNull();
  });
  it("silently discards an aborted simulation refresh", async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() =>
        response({ accessToken: "token", expiresIn: 900 }),
      )
      .mockImplementationOnce(() =>
        response({ email: "operator@srm.local", roles: ["OPERATOR"] }),
      )
      .mockImplementationOnce(
        (_url: string, init?: RequestInit) =>
          new Promise<Response>((_resolve, reject) => {
            init?.signal?.addEventListener("abort", () =>
              reject(new DOMException("Aborted", "AbortError")),
            );
          }),
      );
    stubFetch(fetchMock);
    render(<App />);
    await signIn();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(SIMULATION_DEBOUNCE_MS);
    });

    fireEvent.change(screen.getByLabelText("Product"), {
      target: { value: "POST_DATED_CHEQUE" },
    });
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(screen.queryByRole("alert")).toBeNull();
  });
});
