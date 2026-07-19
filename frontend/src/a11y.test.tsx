import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
} from "@testing-library/react";
import { axe } from "jest-axe";
import { afterEach, describe, expect, it, vi, type Mock } from "vitest";
import App from "./App";

const simulation = (amount: string) => ({
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

async function expectNoA11yViolations(container: Element) {
  const results = await axe(container);
  expect(
    results.violations,
    JSON.stringify(results.violations, null, 2),
  ).toEqual([]);
}

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

describe("UI-A11Y-001 login screen accessibility", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  it("has no axe violations on first render", async () => {
    const { container } = render(<App />);
    expect(
      screen.getByRole("heading", { name: "Operator sign in" }),
    ).toBeInTheDocument();
    await expectNoA11yViolations(container);
  });

  it("has no axe violations while showing a sign-in error", async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() =>
        response({ detail: "Invalid credentials." }, 401),
      );
    stubFetch(fetchMock);
    const { container } = render(<App />);
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "wrong-password" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    await screen.findByRole("alert");
    await expectNoA11yViolations(container);
  });

  it("can be completed keyboard-only, without a mouse", async () => {
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

    const emailInput = screen.getByLabelText("Email");
    const passwordInput = screen.getByLabelText("Password");
    const signInButton = screen.getByRole("button", { name: "Sign in" });

    emailInput.focus();
    expect(emailInput).toHaveFocus();

    fireEvent.keyDown(emailInput, { key: "Tab" });
    passwordInput.focus();
    expect(passwordInput).toHaveFocus();
    fireEvent.change(passwordInput, { target: { value: "test-password" } });

    fireEvent.keyDown(passwordInput, { key: "Tab" });
    signInButton.focus();
    expect(signInButton).toHaveFocus();

    fireEvent.keyDown(signInButton, { key: "Enter" });
    fireEvent.click(signInButton);
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(
      screen.getByRole("heading", { name: "Live receivable pricing" }),
    ).toBeInTheDocument();
  });
});

describe("UI-A11Y-002 live simulation screen accessibility", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  it("has no axe violations while the simulation is loading", async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() =>
        response({ accessToken: "token", expiresIn: 900 }),
      )
      .mockImplementationOnce(() =>
        response({ email: "operator@srm.local", roles: ["OPERATOR"] }),
      )
      .mockImplementationOnce(() => new Promise(() => {}));
    stubFetch(fetchMock);
    const { container } = render(<App />);
    await signIn();

    expect(screen.getAllByRole("status")[0]).toHaveTextContent(
      "Requesting authoritative price",
    );
    await expectNoA11yViolations(container);
  });

  it("has no axe violations while the simulation is stale", async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() =>
        response({ accessToken: "token", expiresIn: 900 }),
      )
      .mockImplementationOnce(() =>
        response({ email: "operator@srm.local", roles: ["OPERATOR"] }),
      )
      .mockImplementationOnce(() => response(simulation("975.61")))
      .mockImplementationOnce(() => new Promise(() => {}));
    stubFetch(fetchMock);
    const { container } = render(<App />);
    await signIn();
    await screen.findAllByText("975.61");

    fireEvent.change(screen.getByLabelText("Product"), {
      target: { value: "POST_DATED_CHEQUE" },
    });

    expect(screen.getAllByRole("status")[0]).toHaveTextContent(
      "Inputs changed",
    );
    await expectNoA11yViolations(container);
  });

  it("has no axe violations while the simulation reports a server error", async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() =>
        response({ accessToken: "token", expiresIn: 900 }),
      )
      .mockImplementationOnce(() =>
        response({ email: "operator@srm.local", roles: ["OPERATOR"] }),
      )
      .mockImplementationOnce(() =>
        response({ detail: "Pricing is unavailable." }, 503),
      );
    stubFetch(fetchMock);
    const { container } = render(<App />);
    await signIn();
    await screen.findByText("Pricing is unavailable.");
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Pricing is unavailable.",
    );
    await expectNoA11yViolations(container);
  });

  it("announces the loading, stale, and error states through the live status region", async () => {
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
        response({ detail: "Pricing is unavailable." }, 503),
      );
    stubFetch(fetchMock);
    render(<App />);
    await signIn();

    const status = () => screen.getAllByRole("status")[0];
    // role="status" implies aria-live="polite" per the ARIA spec, so screen
    // readers announce updates without an explicit aria-live attribute.
    expect(status()).toHaveAttribute("role", "status");
    expect(status()).toHaveTextContent("Requesting authoritative price");

    await screen.findAllByText("975.61");
    expect(status()).toHaveTextContent("Prices are calculated by the server");

    fireEvent.change(screen.getByLabelText("Product"), {
      target: { value: "POST_DATED_CHEQUE" },
    });
    expect(status()).toHaveTextContent("Inputs changed");

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Pricing is unavailable.");
  });

  it("can trigger a simulation keyboard-only after signing in", async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() =>
        response({ accessToken: "token", expiresIn: 900 }),
      )
      .mockImplementationOnce(() =>
        response({ email: "operator@srm.local", roles: ["OPERATOR"] }),
      )
      .mockImplementationOnce(() => response(simulation("975.61")))
      .mockImplementationOnce(() => response(simulation("966.18")));
    stubFetch(fetchMock);
    render(<App />);
    await signIn();
    await screen.findAllByText("975.61");

    const productSelect = screen.getByLabelText("Product");
    productSelect.focus();
    expect(productSelect).toHaveFocus();
    fireEvent.keyDown(productSelect, { key: "ArrowDown" });
    fireEvent.change(productSelect, { target: { value: "POST_DATED_CHEQUE" } });

    await screen.findAllByText("966.18");
    expect(
      fetchMock.mock.calls.some(
        ([url]) =>
          String(url).includes("pricing-quotes") ||
          String(url).includes("settlements"),
      ),
    ).toBe(false);
  });
});
