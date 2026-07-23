import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  api,
  type PricingSimulation,
  type Session,
  type Settlement,
  type SettlementPreview,
} from "../api/client";
import { initialValues } from "../pricing/model";
import { SIMULATION_DEBOUNCE_MS, useLiveSimulation } from "./useLiveSimulation";
import { useSettlementIntent } from "./useSettlementIntent";
import {
  LEDGER_FILTER_DEBOUNCE_MS,
  useStatementFilters,
} from "./useStatementFilters";

const session: Session = {
  accessToken: "workflow-token",
  email: "operator@srm.local",
  expiresAt: Date.now() + 60_000,
  roles: ["OPERATOR"],
};

const simulation: PricingSimulation = {
  faceAmount: "1000.00",
  faceCurrency: "BRL",
  settlementCurrency: "BRL",
  baseRate: "0.01",
  spread: "0.02",
  strategyCode: "INVOICE",
  dayCountConvention: "ACTUAL_DAYS_30_MONTH",
  termInMonths: "1.0000000000",
  discountedAmount: "970.00",
  fxBaseCurrency: "BRL",
  fxQuoteCurrency: "BRL",
  fxRate: "1.0000000000",
  fxSource: "IDENTITY",
  fxObservedAt: "2030-01-01T00:00:00Z",
  settlementAmount: "970.00",
  pricedAt: "2030-01-01T00:00:00Z",
};

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
  localStorage.clear();
  window.history.replaceState(null, "", "/");
});

describe("workflow hook interfaces", () => {
  it("debounces the live server simulation behind its hook boundary", async () => {
    vi.useFakeTimers();
    const simulate = vi.spyOn(api, "simulate").mockResolvedValue(simulation);
    const setFeedback = vi.fn();
    const setFocusAlert = vi.fn();
    const { result } = renderHook(() =>
      useLiveSimulation(
        session,
        initialValues,
        true,
        undefined,
        setFeedback,
        setFocusAlert,
      ),
    );

    expect(result.current.state).toBe("loading");
    expect(simulate).not.toHaveBeenCalled();
    await act(() => vi.advanceTimersByTimeAsync(SIMULATION_DEBOUNCE_MS));

    expect(simulate).toHaveBeenCalledOnce();
    expect(result.current.simulation).toEqual(simulation);
  });

  it("settles only the previewed quote IDs through its intent hook", async () => {
    const preview: SettlementPreview = {
      items: [
        {
          quoteId: "11111111-1111-4111-8111-111111111111",
          receivableId: "22222222-2222-4222-8222-222222222222",
          settlementAmount: "970.00",
        },
      ],
      settlementCurrency: "BRL",
      totalAmount: "970.00",
      asOf: new Date().toISOString(),
      earliestExpiry: new Date(Date.now() + 60_000).toISOString(),
    };
    const settlement: Settlement = {
      settlementId: "33333333-3333-4333-8333-333333333333",
      status: "SETTLED",
      items: preview.items,
      settlementCurrency: "BRL",
      totalAmount: "970.00",
      completedAt: new Date().toISOString(),
    };
    vi.spyOn(api, "previewSettlement").mockResolvedValue(preview);
    const settle = vi.spyOn(api, "settle").mockResolvedValue(settlement);
    const onSettled = vi.fn();
    const { result } = renderHook(() =>
      useSettlementIntent({ session, onSettled }),
    );

    act(() => result.current.toggle(preview.items[0].quoteId, true));
    await act(() => result.current.requestPreview());
    await waitFor(() => expect(result.current.preview).toEqual(preview));
    await act(() => result.current.confirm());

    expect(settle).toHaveBeenCalledWith(
      [preview.items[0].quoteId],
      expect.any(String),
      session.accessToken,
      expect.any(AbortSignal),
    );
    expect(onSettled).toHaveBeenCalledExactlyOnceWith([
      preview.items[0].quoteId,
    ]);
  });

  it("replaces filter history immediately and requests after debounce", async () => {
    vi.useFakeTimers();
    const statement = vi.spyOn(api, "statement").mockResolvedValue({
      entries: [],
      page: 0,
      size: 50,
      hasNext: false,
    });
    const { result } = renderHook(() => useStatementFilters(session, 0));
    await act(async () => undefined);
    expect(statement).toHaveBeenCalledOnce();

    act(() => result.current.change("assetCurrency", "BRL"));
    expect(window.location.search).toContain("assetCurrency=BRL");
    expect(statement).toHaveBeenCalledOnce();

    await act(() => vi.advanceTimersByTimeAsync(LEDGER_FILTER_DEBOUNCE_MS - 1));
    expect(statement).toHaveBeenCalledOnce();
    await act(() => vi.advanceTimersByTimeAsync(1));
    expect(statement).toHaveBeenCalledTimes(2);
  });
});
