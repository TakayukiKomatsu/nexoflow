import {
  useEffect,
  useRef,
  useState,
  type Dispatch,
  type SetStateAction,
} from "react";
import {
  ApiError,
  api,
  type PricingSimulation,
  type Session,
} from "../api/client";
import type { Feedback, FormValues } from "../pricing/model";

export const SIMULATION_DEBOUNCE_MS = 300;

export type LiveSimulationState = "idle" | "loading" | "stale" | "error";

export function useLiveSimulation(
  session: Session,
  values: FormValues,
  canCreate: boolean,
  simulationError: string | undefined,
  setFeedback: Dispatch<SetStateAction<Feedback | undefined>>,
  setFocusAlert: Dispatch<SetStateAction<boolean>>,
) {
  const [simulation, setSimulation] = useState<PricingSimulation>();
  const [state, setState] = useState<LiveSimulationState>("idle");
  const requestId = useRef(0);
  const simulationRef = useRef<PricingSimulation | undefined>(undefined);
  const { faceAmount, faceCurrency, productType, dueDate, settlementCurrency } =
    values;

  useEffect(() => {
    const currentRequest = ++requestId.current;
    if (!canCreate) {
      setSimulation(undefined);
      simulationRef.current = undefined;
      setState("idle");
      setFeedback(undefined);
      return;
    }
    if (simulationError) {
      setSimulation(undefined);
      simulationRef.current = undefined;
      setState("idle");
      return;
    }
    setState(simulationRef.current ? "stale" : "loading");
    setFeedback(undefined);
    const controller = new AbortController();
    const timeout = window.setTimeout(async () => {
      try {
        const result = await api.simulate(
          {
            faceAmount,
            faceCurrency,
            productType,
            dueDate,
            settlementCurrency,
          },
          session.accessToken,
          controller.signal,
        );
        if (currentRequest === requestId.current) {
          simulationRef.current = result;
          setSimulation(result);
          setState("idle");
        }
      } catch (cause) {
        if (controller.signal.aborted || currentRequest !== requestId.current)
          return;
        if (cause instanceof ApiError && cause.status === 401) return;
        setFocusAlert(true);
        setState("error");
        setFeedback({
          text:
            cause instanceof ApiError
              ? cause.message
              : "Simulation is unavailable.",
          kind: "error",
        });
      }
    }, SIMULATION_DEBOUNCE_MS);
    return () => {
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, [
    faceAmount,
    faceCurrency,
    productType,
    dueDate,
    settlementCurrency,
    simulationError,
    canCreate,
    session.accessToken,
    setFeedback,
    setFocusAlert,
  ]);

  return { simulation, state };
}
