import { expireSession, type Session } from "../session";

export type { Session } from "../session";

export type Uuid = string;
export type IsoDate = string;
export type IsoInstant = string;
export type Int32 = number;
export type Int64 = number;
export type Currency = "BRL" | "USD";
export type ProductType = "MERCANTILE_INVOICE" | "POST_DATED_CHEQUE";
export type ReceivableStatus = "REGISTERED" | "SETTLED" | "REVERSED";
export type QuoteStatus = "ACTIVE" | "EXPIRED" | "CONSUMED";
export type SettlementStatus = "COMPLETED";

export const API_OPERATIONS = {
  login: { method: "POST", path: "/auth/login" },
  currentUser: { method: "GET", path: "/users/me" },
  simulate: { method: "POST", path: "/pricing-simulations" },
  createReceivable: { method: "POST", path: "/receivables" },
  createQuote: { method: "POST", path: "/pricing-quotes" },
  previewSettlement: { method: "POST", path: "/settlement-previews" },
  settle: { method: "POST", path: "/settlements" },
  settlement: { method: "GET", path: "/settlements/{settlementId}" },
  statement: { method: "GET", path: "/settlement-statements" },
} as const;

export type LoginRequest = {
  email: string;
  password: string;
};
export type AccessToken = {
  accessToken: string;
  expiresIn: Int64;
  tokenType: string;
};
export type CurrentUser = {
  id: string;
  email: string;
  roles: Session["roles"];
};
export type ReceivableRequest = {
  assignorId: Uuid;
  productType: ProductType;
  faceAmount: string;
  faceCurrency: Currency;
  issueDate: IsoDate;
  dueDate: IsoDate;
};
export type Receivable = {
  id: Uuid;
  assignorId: Uuid;
  productType: ProductType;
  faceAmount: string;
  faceCurrency: Currency;
  issueDate: IsoDate;
  dueDate: IsoDate;
  status: ReceivableStatus;
  version: Int64;
};

export type PricingSimulationRequest = {
  faceAmount: string;
  faceCurrency: Currency;
  productType: ProductType;
  dueDate: IsoDate;
  settlementCurrency: Currency;
};
export type QuoteRequest = {
  receivableId: Uuid;
  settlementCurrency: Currency;
};
export type QuoteIdsRequest = {
  quoteIds: Uuid[];
};
export type SettlementPathParameters = {
  settlementId: Uuid;
};
export type SettlementHeaders = {
  "Idempotency-Key": string;
};
export type StatementFilters = {
  from?: IsoInstant;
  to?: IsoInstant;
  assignorId?: Uuid;
  assetCurrency?: Currency;
  settlementCurrency?: Currency;
  productType?: ProductType;
  page?: Int32;
  size?: Int32;
};

export type PricingBreakdown = {
  faceAmount: string;
  faceCurrency: Currency;
  settlementCurrency: Currency;
  baseRate: string;
  spread: string;
  strategyCode: string;
  dayCountConvention: string;
  termInMonths: string;
  discountedAmount: string;
  fxBaseCurrency: Currency;
  fxQuoteCurrency: Currency;
  fxRate: string;
  fxSource: string;
  fxObservedAt: IsoInstant;
  settlementAmount: string;
  pricedAt: IsoInstant;
};
export type PricingSimulation = PricingBreakdown;

export type PricingQuote = {
  id: Uuid;
  receivableId: Uuid;
  productType: ProductType;
  dueDate: IsoDate;
  pricing: PricingBreakdown;
  expiresAt: IsoInstant;
  status: QuoteStatus;
  createdBy: string;
};
export type SettlementItem = {
  quoteId: Uuid;
  receivableId: Uuid;
  settlementAmount: string;
};
export type SettlementPreview = {
  items: SettlementItem[];
  settlementCurrency: Currency;
  totalAmount: string;
  asOf: IsoInstant;
  earliestExpiry: IsoInstant;
};
export type Settlement = {
  settlementId: Uuid;
  status: SettlementStatus;
  items: SettlementItem[];
  settlementCurrency: Currency;
  totalAmount: string;
  completedAt: IsoInstant;
};
export type StatementEntry = {
  entryId: Uuid;
  entryType: "SETTLEMENT" | "REVERSAL";
  signedAmount: string;
  effectiveAt: IsoInstant;
  settlementId: Uuid;
  reversalId: Uuid | null;
  assignorId: Uuid;
  assetCurrency: Currency;
  settlementCurrency: Currency;
  productType: ProductType;
  receivableId: Uuid;
};
export type StatementPage = {
  entries: StatementEntry[];
  page: Int32;
  size: Int32;
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
type ApiOperation = (typeof API_OPERATIONS)[keyof typeof API_OPERATIONS];
type ApiRequestInit = Omit<RequestInit, "method"> & {
  pathParameters?: Record<string, string>;
  query?: URLSearchParams;
};

function operationPath(
  template: string,
  pathParameters: Record<string, string> = {},
): string {
  return template.replaceAll(/\{([^}]+)\}/g, (_, name: string) => {
    const value = pathParameters[name];
    if (!value) throw new Error(`Missing API path parameter ${name}.`);
    return encodeURIComponent(value);
  });
}

async function request<T>(
  operation: ApiOperation,
  init: ApiRequestInit = {},
  token?: string,
): Promise<T> {
  const { pathParameters, query, ...requestInit } = init;
  const path = operationPath(operation.path, pathParameters);
  const queryString = query?.toString();
  const response = await fetch(
    `${API_ROOT}${path}${queryString ? `?${queryString}` : ""}`,
    {
      ...requestInit,
      method: operation.method,
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...requestInit.headers,
      },
    },
  );
  if (!response.ok) {
    const body = (await response.json().catch(() => undefined)) as
      ProblemDetail | undefined;
    const error = new ApiError(
      response.status,
      body?.code,
      body?.detail ?? `Request failed (${response.status}).`,
      body?.correlationId,
    );
    if (response.status === 401) expireSession();
    throw error;
  }
  return response.json() as Promise<T>;
}

function optionalInteger(
  search: URLSearchParams,
  name: string,
): number | undefined {
  const value = search.get(name);
  return value === null ? undefined : Number(value);
}

function optionalCurrency(value: string | null): Currency | undefined {
  return value === "BRL" || value === "USD" ? value : undefined;
}

function optionalProductType(value: string | null): ProductType | undefined {
  return value === "MERCANTILE_INVOICE" || value === "POST_DATED_CHEQUE"
    ? value
    : undefined;
}

export function statementFiltersFromSearch(
  search: URLSearchParams,
): StatementFilters {
  return {
    from: search.get("from") ?? undefined,
    to: search.get("to") ?? undefined,
    assignorId: search.get("assignorId") ?? undefined,
    assetCurrency: optionalCurrency(search.get("assetCurrency")),
    settlementCurrency: optionalCurrency(search.get("settlementCurrency")),
    productType: optionalProductType(search.get("productType")),
    page: optionalInteger(search, "page"),
    size: optionalInteger(search, "size"),
  };
}

export const api = {
  async login(input: LoginRequest): Promise<Session> {
    const token = await request<AccessToken>(API_OPERATIONS.login, {
      body: JSON.stringify(input),
    });
    const me = await request<CurrentUser>(
      API_OPERATIONS.currentUser,
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
      API_OPERATIONS.simulate,
      { body: JSON.stringify(input), signal },
      token,
    );
  },
  createReceivable(input: ReceivableRequest, token: string) {
    const requestBody: ReceivableRequest = {
      assignorId: input.assignorId,
      productType: input.productType,
      faceAmount: input.faceAmount,
      faceCurrency: input.faceCurrency,
      issueDate: input.issueDate,
      dueDate: input.dueDate,
    };
    return request<Receivable>(
      API_OPERATIONS.createReceivable,
      { body: JSON.stringify(requestBody) },
      token,
    );
  },
  createQuote(
    receivableId: string,
    settlementCurrency: Currency,
    token: string,
  ) {
    const input: QuoteRequest = { receivableId, settlementCurrency };
    return request<PricingQuote>(
      API_OPERATIONS.createQuote,
      { body: JSON.stringify(input) },
      token,
    );
  },
  previewSettlement(quoteIds: string[], token: string, signal?: AbortSignal) {
    const input: QuoteIdsRequest = { quoteIds };
    return request<SettlementPreview>(
      API_OPERATIONS.previewSettlement,
      { body: JSON.stringify(input), signal },
      token,
    );
  },
  settle(
    quoteIds: string[],
    idempotencyKey: string,
    token: string,
    signal?: AbortSignal,
  ) {
    const input: QuoteIdsRequest = { quoteIds };
    const headers: SettlementHeaders = { "Idempotency-Key": idempotencyKey };
    return request<Settlement>(
      API_OPERATIONS.settle,
      { body: JSON.stringify(input), headers, signal },
      token,
    );
  },
  settlement(settlementId: string, token: string, signal?: AbortSignal) {
    const pathParameters: SettlementPathParameters = { settlementId };
    return request<Settlement>(
      API_OPERATIONS.settlement,
      { pathParameters: { ...pathParameters }, signal },
      token,
    );
  },
  statement(filters: StatementFilters, token: string, signal?: AbortSignal) {
    const query = new URLSearchParams();
    for (const [name, value] of Object.entries(filters)) {
      if (value !== undefined) query.set(name, String(value));
    }
    return request<StatementPage>(
      API_OPERATIONS.statement,
      { query, signal },
      token,
    );
  },
};
