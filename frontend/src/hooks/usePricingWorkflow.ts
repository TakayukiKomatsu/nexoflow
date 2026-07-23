import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ApiError, api, type PricingQuote, type Session } from "../api/client";
import {
  initialValues,
  pricingFingerprint,
  pricingValidation,
  registrationValidation,
  type Feedback,
  type FormValues,
} from "../pricing/model";
import { useLiveSimulation } from "./useLiveSimulation";

export function usePricingWorkflow(session: Session) {
  const [values, setValues] = useState(initialValues);
  const [quotes, setQuotes] = useState<PricingQuote[]>([]);
  const [receivableId, setReceivableId] = useState<string>();
  const [receivablePending, setReceivablePending] = useState(false);
  const [quotePending, setQuotePending] = useState(false);
  const [showAssignorError, setShowAssignorError] = useState(false);
  const [focusAlert, setFocusAlert] = useState(false);
  const [feedback, setFeedback] = useState<Feedback>();
  const receivableIdRef = useRef<string | undefined>(undefined);
  const receivablePendingRef = useRef(false);
  const quotePendingRef = useRef(false);
  const formFingerprintRef = useRef(pricingFingerprint(initialValues));
  const alertRef = useRef<HTMLParagraphElement>(null);
  const pricingErrors = useMemo(() => pricingValidation(values), [values]);
  const registrationErrors = useMemo(
    () => registrationValidation(values),
    [values],
  );
  const fieldErrors = {
    ...registrationErrors,
    assignorId: showAssignorError ? registrationErrors.assignorId : undefined,
  };
  const simulationError = Object.values(pricingErrors)[0];
  const canCreate =
    session.roles.includes("OPERATOR") || session.roles.includes("ADMIN");
  const canViewLedger = session.roles.some((role) =>
    ["OPERATOR", "ANALYST", "ADMIN", "AUDITOR"].includes(role),
  );
  const { simulation, state } = useLiveSimulation(
    session,
    values,
    canCreate,
    simulationError,
    setFeedback,
    setFocusAlert,
  );

  useEffect(() => {
    if (focusAlert && feedback?.kind === "error") {
      alertRef.current?.focus();
      setFocusAlert(false);
    }
  }, [focusAlert, feedback]);

  function set<K extends keyof FormValues>(key: K, value: FormValues[K]) {
    const next = { ...values, [key]: value };
    formFingerprintRef.current = pricingFingerprint(next);
    setValues(next);
    receivableIdRef.current = undefined;
    setReceivableId(undefined);
    if (key === "assignorId") setShowAssignorError(false);
    setFeedback(undefined);
    setFocusAlert(false);
  }

  async function createReceivable() {
    if (receivablePendingRef.current) return;
    const errors = registrationValidation(values);
    setShowAssignorError(true);
    const firstInvalidField = (
      [
        "faceAmount",
        "faceCurrency",
        "settlementCurrency",
        "issueDate",
        "dueDate",
        "assignorId",
      ] as const
    ).find((key) => errors[key]);
    if (firstInvalidField) {
      setFeedback({ text: errors[firstInvalidField]!, kind: "error" });
      document.getElementById(firstInvalidField)?.focus();
      return;
    }
    const requestFingerprint = formFingerprintRef.current;
    receivablePendingRef.current = true;
    setReceivablePending(true);
    setFeedback(undefined);
    try {
      const receivable = await api.createReceivable(
        values,
        session.accessToken,
      );
      if (requestFingerprint !== formFingerprintRef.current) return;
      receivableIdRef.current = receivable.id;
      setReceivableId(receivable.id);
      setFeedback({
        text: `Receivable ${receivable.id} registered. You can now create a quote.`,
        kind: "success",
      });
    } catch (cause) {
      if (requestFingerprint !== formFingerprintRef.current) return;
      setFeedback({
        text:
          cause instanceof ApiError
            ? cause.message
            : "Could not register receivable.",
        kind: "error",
      });
      setFocusAlert(true);
    } finally {
      receivablePendingRef.current = false;
      setReceivablePending(false);
    }
  }

  async function createQuote() {
    if (!receivableId || quotePendingRef.current) return;
    const requestFingerprint = formFingerprintRef.current;
    const requestReceivableId = receivableId;
    quotePendingRef.current = true;
    setQuotePending(true);
    setFeedback(undefined);
    try {
      const result = await api.createQuote(
        requestReceivableId,
        values.settlementCurrency,
        session.accessToken,
      );
      setQuotes((current) => [...current, result]);
      if (
        requestFingerprint !== formFingerprintRef.current ||
        requestReceivableId !== receivableIdRef.current
      )
        return;
      setFeedback({
        text: `Quote ${result.id} created; it expires at ${result.expiresAt}.`,
        kind: "success",
      });
    } catch (cause) {
      if (
        requestFingerprint !== formFingerprintRef.current ||
        requestReceivableId !== receivableIdRef.current
      )
        return;
      setFeedback({
        text:
          cause instanceof ApiError ? cause.message : "Could not create quote.",
        kind: "error",
      });
      setFocusAlert(true);
    } finally {
      quotePendingRef.current = false;
      setQuotePending(false);
    }
  }

  const consumeQuotes = useCallback((consumedQuoteIds: string[]) => {
    setQuotes((current) =>
      current.filter(({ id }) => !consumedQuoteIds.includes(id)),
    );
  }, []);

  return {
    alertRef,
    canCreate,
    canViewLedger,
    consumeQuotes,
    createQuote,
    createReceivable,
    feedback,
    fieldErrors,
    quotePending,
    quotes,
    receivableId,
    receivablePending,
    set,
    simulation,
    state,
    values,
  };
}

export type PricingWorkflow = ReturnType<typeof usePricingWorkflow>;
