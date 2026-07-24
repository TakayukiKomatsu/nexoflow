import { ApiError } from "../api/client";

export type SettlementIntent = {
  email: string;
  quoteIds: string[];
  key: string;
};

export const SETTLEMENT_INTENT_STORAGE_KEY = "srm-settlement-intent";

export function loadSettlementIntent(
  email: string,
): SettlementIntent | undefined {
  try {
    const value = localStorage.getItem(SETTLEMENT_INTENT_STORAGE_KEY);
    if (!value) return undefined;
    const intent = JSON.parse(value) as Partial<SettlementIntent>;
    if (
      intent.email !== email ||
      !Array.isArray(intent.quoteIds) ||
      !intent.quoteIds.every((id) => typeof id === "string") ||
      typeof intent.key !== "string"
    ) {
      return undefined;
    }
    return { email, quoteIds: intent.quoteIds, key: intent.key };
  } catch {
    return undefined;
  }
}

export function makeIdempotencyKey(): string {
  return (
    globalThis.crypto?.randomUUID?.() ??
    `settlement-${Date.now()}-${Math.random()}`
  );
}

export function apiErrorMessage(cause: unknown, fallback: string): string {
  if (!(cause instanceof ApiError)) return fallback;
  if (cause.status === 401) return "Your session has expired. Sign in again.";
  if (cause.status === 403) {
    return "Your role is not allowed to perform this action.";
  }
  return cause.message;
}
