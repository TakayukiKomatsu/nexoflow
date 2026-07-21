export type Session = {
  accessToken: string;
  expiresAt: number;
  email: string;
  roles: string[];
};
export type PricingSimulationRequest = {
  faceAmount: string;
  faceCurrency: string;
  productType: string;
  dueDate: string;
  settlementCurrency: string;
};
export type PricingSimulation = PricingSimulationRequest & {
  baseRate: string;
  spread: string;
  strategyCode: string;
  dayCountConvention: string;
  termInMonths: string;
  discountedAmount: string;
  fxBaseCurrency: string;
  fxQuoteCurrency: string;
  fxRate: string;
  fxSource: string;
  fxObservedAt: string;
  settlementAmount: string;
  pricedAt: string;
};
export type PricingBreakdown = Omit<
  PricingSimulation,
  "productType" | "dueDate"
>;

export type PricingQuote = {
  id: string;
  receivableId: string;
  productType: string;
  dueDate: string;
  pricing: PricingBreakdown;
  expiresAt: string;
  status: string;
};
export type SettlementPreview = {
  items: Array<{
    quoteId: string;
    receivableId: string;
    settlementAmount: string;
  }>;
  settlementCurrency: string;
  totalAmount: string;
  asOf: string;
  earliestExpiry: string;
};
export type Settlement = SettlementPreview & {
  settlementId: string;
  status: string;
  completedAt: string;
};
export type StatementEntry = {
  entryId: string;
  entryType: "SETTLEMENT" | "REVERSAL";
  signedAmount: string;
  effectiveAt: string;
  settlementId: string;
  reversalId?: string;
  assignorId: string;
  assetCurrency: string;
  settlementCurrency: string;
  productType: string;
  receivableId: string;
};
export type StatementPage = {
  entries: StatementEntry[];
  page: number;
  size: number;
  hasNext: boolean;
};

export type ProblemDetail = {
  status?: number;
  detail?: string;
  code?: string;
  correlationId?: string;
  violations?: Array<{ field: string; message: string }>;
};

export class ApiError extends Error {
  readonly status: number;
  readonly code: string | undefined;
  readonly correlationId?: string;

  constructor(
    status: number,
    code: string | undefined,
    message: string,
    correlationId?: string,
  ) {
    super(message);
    this.status = status;
    this.code = code;
    this.correlationId = correlationId;
  }
}
const API_ROOT = import.meta.env.VITE_API_ROOT ?? "/api/v1";

async function request<T>(
  path: string,
  init: RequestInit = {},
  token?: string,
): Promise<T> {
  const response = await fetch(`${API_ROOT}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => undefined)) as
      ProblemDetail | undefined;
    throw new ApiError(
      response.status,
      body?.code,
      body?.detail ?? `Request failed (${response.status}).`,
      body?.correlationId,
    );
  }
  return response.json() as Promise<T>;
}

export const api = {
  async login(input: { email: string; password: string }): Promise<Session> {
    const token = await request<{ accessToken: string; expiresIn: number }>(
      "/auth/login",
      { method: "POST", body: JSON.stringify(input) },
    );
    const me = await request<{ email: string; roles: string[] }>(
      "/users/me",
      {},
      token.accessToken,
    );
    return {
      accessToken: token.accessToken,
      expiresAt: Date.now() + token.expiresIn * 1000,
      email: me.email,
      roles: me.roles,
    };
  },
  simulate(
    input: PricingSimulationRequest,
    token: string,
    signal: AbortSignal,
  ) {
    return request<PricingSimulation>(
      "/pricing-simulations",
      { method: "POST", body: JSON.stringify(input), signal },
      token,
    );
  },
  createReceivable(
    input: PricingSimulationRequest & { assignorId: string; issueDate: string },
    token: string,
  ) {
    return request<{ id: string }>(
      "/receivables",
      {
        method: "POST",
        body: JSON.stringify({
          assignorId: input.assignorId,
          productType: input.productType,
          faceAmount: input.faceAmount,
          faceCurrency: input.faceCurrency,
          issueDate: input.issueDate,
          dueDate: input.dueDate,
        }),
      },
      token,
    );
  },
  createQuote(receivableId: string, settlementCurrency: string, token: string) {
    return request<PricingQuote>(
      "/pricing-quotes",
      {
        method: "POST",
        body: JSON.stringify({ receivableId, settlementCurrency }),
      },
      token,
    );
  },
  previewSettlement(quoteIds: string[], token: string, signal?: AbortSignal) {
    return request<SettlementPreview>(
      "/settlement-previews",
      { method: "POST", body: JSON.stringify({ quoteIds }), signal },
      token,
    );
  },
  settle(
    quoteIds: string[],
    idempotencyKey: string,
    token: string,
    signal?: AbortSignal,
  ) {
    return request<Settlement>(
      "/settlements",
      {
        method: "POST",
        body: JSON.stringify({ quoteIds }),
        headers: { "Idempotency-Key": idempotencyKey },
        signal,
      },
      token,
    );
  },
  settlement(settlementId: string, token: string, signal?: AbortSignal) {
    return request<Settlement>(
      `/settlements/${encodeURIComponent(settlementId)}`,
      { signal },
      token,
    );
  },
  statement(search: URLSearchParams, token: string, signal?: AbortSignal) {
    const suffix = search.toString();
    return request<StatementPage>(
      `/settlement-statements${suffix ? `?${suffix}` : ""}`,
      { signal },
      token,
    );
  },
};
