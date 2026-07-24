import { useEffect, useRef, useState } from "react";
import {
  ApiError,
  api,
  type Session,
  type Settlement,
  type SettlementPreview,
} from "../api/client";
import {
  SETTLEMENT_INTENT_STORAGE_KEY,
  apiErrorMessage,
  loadSettlementIntent,
  makeIdempotencyKey,
  type SettlementIntent,
} from "../settlement/model";

type PreviewState = { quoteKey: string; value: SettlementPreview };

export const SETTLEMENT_TIMEOUT_MS = 10_000;

export type UseSettlementIntentOptions = {
  session: Session;
  onSettled?: (consumedQuoteIds: string[]) => void;
};

export function useSettlementIntent({
  session,
  onSettled = () => undefined,
}: UseSettlementIntentOptions) {
  const [selected, setSelected] = useState<string[]>([]);
  const [previewState, setPreviewState] = useState<PreviewState>();
  const [previewExpired, setPreviewExpired] = useState(false);
  const [intent, setIntent] = useState<SettlementIntent | undefined>(() =>
    loadSettlementIntent(session.email),
  );
  const [result, setResult] = useState<Settlement>();
  const [message, setMessage] = useState<string>();
  const [previewPending, setPreviewPending] = useState(false);
  const [settlementPending, setSettlementPending] = useState(false);
  const [ledgerRevision, setLedgerRevision] = useState(0);
  const previewRequestId = useRef(0);
  const previewAbort = useRef<AbortController | undefined>(undefined);
  const quoteIds = selected;
  const quoteKey = quoteIds.join(",");
  const currentQuoteKey = useRef(quoteKey);
  currentQuoteKey.current = quoteKey;
  const preview = previewState?.value;
  const previewMatchesSelection = previewState?.quoteKey === quoteKey;
  const intentMatchesSelection =
    !intent || intent.quoteIds.join(",") === quoteKey;
  const canSettle =
    session.roles.includes("OPERATOR") || session.roles.includes("ADMIN");

  useEffect(() => {
    const recovered = loadSettlementIntent(session.email);
    setIntent(recovered);
    setResult(undefined);
    setSelected(recovered?.quoteIds ?? []);
  }, [session.email]);

  useEffect(() => {
    previewRequestId.current += 1;
    previewAbort.current?.abort();
    setPreviewPending(false);
    setPreviewState(undefined);
    setPreviewExpired(false);
    setMessage(undefined);
  }, [quoteKey]);

  useEffect(() => {
    if (!preview) {
      setPreviewExpired(false);
      return;
    }
    const expiresAt = Date.parse(preview.earliestExpiry);
    let timeout: number | undefined;
    const scheduleExpiry = () => {
      const remaining = expiresAt - Date.now();
      if (remaining <= 0) {
        setPreviewExpired(true);
        return;
      }
      timeout = window.setTimeout(
        scheduleExpiry,
        Math.min(remaining, 2_147_483_647),
      );
    };
    setPreviewExpired(false);
    scheduleExpiry();
    return () => {
      if (timeout !== undefined) window.clearTimeout(timeout);
    };
  }, [preview]);

  useEffect(
    () => () => {
      previewAbort.current?.abort();
    },
    [],
  );

  function toggle(id: string, checked: boolean) {
    setResult(undefined);
    setSelected((old) =>
      checked ? [...old, id] : old.filter((value) => value !== id),
    );
  }

  async function requestPreview() {
    if (!canSettle || !quoteIds.length) return;
    previewAbort.current?.abort();
    const controller = new AbortController();
    previewAbort.current = controller;
    const requestId = ++previewRequestId.current;
    const requestedQuoteKey = quoteKey;
    setPreviewPending(true);
    setMessage(undefined);
    try {
      const value = await api.previewSettlement(
        quoteIds,
        session.accessToken,
        controller.signal,
      );
      if (
        requestId !== previewRequestId.current ||
        requestedQuoteKey !== currentQuoteKey.current
      ) {
        return;
      }
      setPreviewState({ quoteKey: requestedQuoteKey, value });
    } catch (cause) {
      if (cause instanceof DOMException && cause.name === "AbortError") return;
      if (
        requestId !== previewRequestId.current ||
        requestedQuoteKey !== currentQuoteKey.current
      ) {
        return;
      }
      setPreviewState(undefined);
      setMessage(
        apiErrorMessage(cause, "Could not obtain a settlement preview."),
      );
    } finally {
      if (requestId === previewRequestId.current) {
        setPreviewPending(false);
        if (previewAbort.current === controller) {
          previewAbort.current = undefined;
        }
      }
    }
  }

  function clearIntent() {
    localStorage.removeItem(SETTLEMENT_INTENT_STORAGE_KEY);
    setIntent(undefined);
    setPreviewState(undefined);
  }

  async function confirm() {
    if (!canSettle) return;
    if (!intentMatchesSelection) {
      setMessage(
        "Cancel the saved settlement intent before confirming a different quote selection.",
      );
      return;
    }
    if (
      !preview ||
      !previewMatchesSelection ||
      previewExpired ||
      Date.parse(preview.earliestExpiry) <= Date.now()
    ) {
      setPreviewState(undefined);
      setMessage(
        "This preview is stale or expired. Request a fresh server preview.",
      );
      return;
    }
    const reusingIntent = intent && intent.quoteIds.join(",") === quoteKey;
    const nextIntent = reusingIntent
      ? intent
      : {
          email: session.email,
          quoteIds: [...quoteIds],
          key: makeIdempotencyKey(),
        };
    localStorage.setItem(
      SETTLEMENT_INTENT_STORAGE_KEY,
      JSON.stringify(nextIntent),
    );
    setIntent(nextIntent);
    setSettlementPending(true);
    setMessage(undefined);
    const controller = new AbortController();
    let timedOut = false;
    const timeout = window.setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, SETTLEMENT_TIMEOUT_MS);
    try {
      const settlement = await api.settle(
        nextIntent.quoteIds,
        nextIntent.key,
        session.accessToken,
        controller.signal,
      );
      setResult(settlement);
      clearIntent();
      setSelected([]);
      onSettled(nextIntent.quoteIds);
      setLedgerRevision((current) => current + 1);
    } catch (cause) {
      if (cause instanceof ApiError) {
        switch (cause.code) {
          case "IDEMPOTENCY_KEY_REUSED":
            clearIntent();
            setMessage(
              "This key belongs to a different request. Request a fresh preview.",
            );
            return;
          case "ALREADY_SETTLED":
            clearIntent();
            setMessage(
              "A selected receivable is already settled. Review the ledger.",
            );
            return;
          case "PRICING_QUOTE_EXPIRED":
            clearIntent();
            setMessage(
              "A pricing quote expired. Create a fresh quote and preview.",
            );
            return;
        }
      }
      const fallback = timedOut
        ? "The settlement request timed out and the outcome is unknown."
        : "The outcome is unknown.";
      setMessage(
        `${apiErrorMessage(cause, fallback)} Retry uses the same settlement intent.`,
      );
    } finally {
      window.clearTimeout(timeout);
      setSettlementPending(false);
    }
  }

  function cancel() {
    clearIntent();
    setMessage("Settlement intent cancelled.");
  }

  return {
    canSettle,
    cancel,
    confirm,
    intent,
    intentMatchesSelection,
    ledgerRevision,
    message,
    preview,
    previewExpired,
    previewMatchesSelection,
    previewPending,
    quoteIds,
    requestPreview,
    result,
    selected,
    settlementPending,
    toggle,
  };
}

export type SettlementIntentWorkflow = ReturnType<typeof useSettlementIntent>;
