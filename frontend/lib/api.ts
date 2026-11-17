import { clearCurrentUser } from "@/lib/auth";

export interface EndpointResponse {
  id: string;
  macAddress: string;
  ipAddress: string | null;
  hostname: string | null;
  osName: string | null;
  osVersion: string | null;
  connected: boolean;
  lastSeenAt: string;
}

export type AssessmentStatus = "COMPLIANT" | "NON_COMPLIANT" | "ERROR";

export interface CheckResultResponse {
  id: string;
  checkType: string;
  status: AssessmentStatus;
  details: Record<string, unknown> | null;
  createdAt: string;
}

export interface AssessmentResponse {
  id: string;
  endpointId: string;
  jobId: string | null;
  status: AssessmentStatus;
  detail: string | null;
  startedAt: string;
  completedAt: string | null;
  checks: CheckResultResponse[];
}

export type HardwareBand = "HEALTHY" | "WARNING" | "DEGRADED" | "CRITICAL";

export interface HardwareHealthResponse {
  id: string;
  endpointId: string;
  jobId: string | null;
  manufacturer: string | null;
  model: string | null;
  serialNumber: string | null;
  biosVersion: string | null;
  cpuScore: number | null;
  memoryScore: number | null;
  storageScore: number | null;
  batteryScore: number | null;
  overallScore: number | null;
  overallBand: HardwareBand | null;
  hardwareEventCount: number | null;
  warrantyStatus: string | null;
  warrantyDaysRemaining: number | null;
  collectedAt: string;
  succeeded: boolean;
  errorMessage: string | null;
  lastAttemptFailedAt: string | null;
  lastAttemptError: string | null;
  recommendations: {
    priority: string;
    area: string;
    action: string;
  }[];
}

export type JobType =
  | "POSTURE_CHECK"
  | "HARDWARE_CHECK"
  | "DIAGNOSTIC_CHECK"
  | "SECURITY_CHECK";

export type JobStatus = "QUEUED" | "RUNNING" | "COMPLETE" | "FAILED";

export interface JobResponse {
  id: string;
  endpointId: string;
  macAddress: string;
  jobType: JobType;
  status: JobStatus;
  priority: number;
  attemptCount: number;
  maxAttempts: number;
  errorMessage: string | null;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
}

export interface IseActionAudit {
  id: string;
  endpointId: string;
  actionType: "SHARE_POSTURE" | "RESTRICT" | "CLEAR_RESTRICTION";
  operator: string | null;
  succeeded: boolean;
  detail: string | null;
  occurredAt: string;
}

export interface IseActionState {
  endpointId: string;
  actionType: "RESTRICT" | "CLEAR_RESTRICTION" | "SHARE_POSTURE";
  succeeded: boolean;
  operator: string | null;
  detail: string | null;
  occurredAt: string;
}

export interface IseStatus {
  reachable: boolean;
  lastSuccessAt: string | null;
  lastError: string | null;
  pollIntervalSeconds?: number;
  enforcementMode?: string;
}

export interface SessionEvent {
  id: string;
  eventType: "CONNECTED" | "DISCONNECTED";
  ipAddress: string | null;
  eventAt: string;
}

export interface DashboardSummary {
  total: number;
  connected: number;
  notConnected: number;
  compliant: number;
  nonCompliant: number;
  error: number;
  unassessed: number;
  stale: number;
}

export interface TrendPoint {
  date: string;
  assessed: number;
  compliantPercent: number | null;
}

export interface CategoryRate {
  checkType: string;
  total: number;
  passing: number;
  passPercent: number;
}

/** Matches InventoryController.AppRow. */
export interface AppRow {
  name: string;
  version?: string | null;
  publisher?: string | null;
  hostname?: string | null;
  macAddress: string;
  status?: string | null;
  summary?: string | null;
}

/** Matches InventoryController.PortRow. */
export interface PortRow {
  port: number;
  process?: string | null;
  pid?: number | null;
  reachable?: boolean | null;
  hostname?: string | null;
  macAddress: string;
  status?: string | null;
}

/** One version of the application policy (required / blocked apps). */
export interface AppPolicy {
  id: string;
  name: string;
  version: number;
  requiredApps: string[];
  blockedApps: string[];
  createdBy: string | null;
  createdAt: string;
}

export interface SystemHealth {
  status: "UP" | "DEGRADED" | "DOWN";
  checkedAt: string;
  database: {
    reachable: boolean;
    error: string | null;
  };
  ise: {
    reachable: boolean;
    lastSuccessAt: string | null;
    lastError: string | null;
  };
  queue: {
    queued: number;
    running: number;
    complete: number;
    failed: number;
    oldestQueuedAgeSeconds: number | null;
    failedLast24h: number;
  };
  workers: {
    enabled: boolean;
    threads: number;
    mode?: "LOCAL" | "REMOTE_PENDING";
  };
  warnings: string[];
}

export type UserRole = "ADMIN" | "OPERATOR" | "ANALYST" | "VIEWER";

/** Matches UserController.UserView. */
export interface UserView {
  id: string;
  username: string;
  role: UserRole;
  enabled: boolean;
  createdAt: string;
  locked: boolean;
}

// ── Warranty types ─────────────────────────────────────────────────────────────
export interface WarrantyView {
  id: string;
  serialNumber: string;
  vendor: string;
  expiresOn: string;
  productName: string | null;
  daysRemaining: number;
  status: "COVERED" | "EXPIRING_SOON" | "EXPIRED";
  source: string;
  uploadedBy: string;
  uploadedAt: string;
}

// ── Endpoint 360 diagnostics ──────────────────────────────────────────────────

export type DiagnosticStatus = "OK" | "WINRM_UNAVAILABLE" | "FAILED";

export type RiskLevel = "NONE" | "LOW" | "MEDIUM" | "HIGH";

export interface DiagnosticDeduction {
  check: string;
  points: number;
  reason: string;
}

export interface DiagnosticResponse {
  id: string;
  endpointId: string;
  jobId: string | null;
  status: DiagnosticStatus;
  score: number | null;
  band: HardwareBand | null;
  deductions: DiagnosticDeduction[] | null;
  results: Record<string, unknown>;
  errorMessage: string | null;
  collectedAt: string;
}

export interface SecurityFinding {
  type: string;
  severity: Exclude<RiskLevel, "NONE">;
  title: string;
  detail: string;
  evidence: Record<string, unknown>;
}

export interface SecurityIndicatorResponse {
  id: string;
  endpointId: string;
  jobId: string | null;
  status: "OK" | "WINRM_UNAVAILABLE" | "FAILED";
  riskLevel: RiskLevel | null;
  findings: SecurityFinding[] | null;
  summary: Record<string, unknown> | null;
  errorMessage: string | null;
  collectedAt: string;
}

export interface WarrantyUploadResult {
  savedCount: number;
  rowErrors: string[];
}

const TOKEN_KEY = "vece_token";

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return sessionStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
  sessionStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  sessionStorage.removeItem(TOKEN_KEY);
}

export interface EndpointListItem extends EndpointResponse {
  postureStatus: AssessmentStatus | null;
  postureAt: string | null;
}

export interface PageResponse<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface HardwareListItem {
  endpointId: string;
  hostname: string | null;
  macAddress: string;
  state: "OK" | "FAILED" | "NO_REPORT";
  manufacturer: string | null;
  model: string | null;
  cpuScore: number | null;
  memoryScore: number | null;
  storageScore: number | null;
  batteryScore: number | null;
  overallScore: number | null;
  overallBand: HardwareBand | null;
  collectedAt: string | null;
  lastAttemptFailedAt: string | null;
  lastAttemptError: string | null;
  recommendationCount: number;
}

export interface HardwareSummary {
  total: number;
  withReport: number;
  avgScore: number;
  criticalOrDegraded: number;
  batteryWarnings: number;
  lastAttemptFailed: number;
  neverCollected: number;
  noReport: number;
  recommendations: number;
}

export interface EndpointBrief {
  hostname: string | null;
  macAddress: string;
  ipAddress: string | null;
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly body: unknown = null,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function readResponseBody(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) return null;

  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
}

function responseMessage(body: unknown, fallback: string): string {
  if (typeof body === "string" && body.trim()) return body;
  if (body && typeof body === "object") {
    const record = body as Record<string, unknown>;
    if (typeof record.message === "string" && record.message.trim()) return record.message;
    if (typeof record.error === "string" && record.error.trim()) return record.error;
  }
  return fallback;
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers = new Headers(init.headers);
  const isFormData = typeof FormData !== "undefined" && init.body instanceof FormData;

  headers.set("Accept", "application/json");
  if (init.body != null && !isFormData && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (token && !headers.has("Authorization")) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  let res: Response;
  try {
    res = await fetch(path, { ...init, headers });
  } catch {
    throw new ApiError("Could not connect to the backend. Check the connection and try again.", 0);
  }

  if (res.status === 401) {
    if (path === "/api/v1/auth/login") {
      throw new ApiError(
        "Invalid username or password. Accounts are locked for a few minutes after repeated failed attempts.",
        res.status,
      );
    }

    clearToken();
    clearCurrentUser();

    if (typeof window !== "undefined") {
      window.location.href = "/login";
    }

    throw new ApiError("Your session has expired. Sign in again.", res.status);
  }

  const body = await readResponseBody(res);
  if (!res.ok) {
    const fallback = res.status === 403 ? "Your role does not allow this action" : `Request failed: ${res.status}`;
    throw new ApiError(responseMessage(body, fallback), res.status, body);
  }

  return body as T;
}

export const api = {
  login: (username: string, password: string) =>
    request<{
      token: string;
      username: string;
      role: string;
    }>("/api/v1/auth/login", {
      method: "POST",
      body: JSON.stringify({
        username,
        password,
      }),
    }),

  getEndpoint: (id: string) =>
    request<EndpointResponse>(`/api/v1/endpoints/${id}`),

  latestPosture: (id: string) =>
    request<AssessmentResponse | null>(
      `/api/v1/endpoints/${id}/posture/latest`,
    ),

  latestPostureOrNull: (id: string) =>
    request<AssessmentResponse | null>(
      `/api/v1/endpoints/${id}/posture/latest`,
    ),

  postureHistory: (id: string) =>
    request<AssessmentResponse[]>(`/api/v1/endpoints/${id}/posture`),

  latestHardwareOrNull: (id: string) =>
    request<HardwareHealthResponse | null>(
      `/api/v1/endpoints/${id}/hardware-health/latest`,
    ),

  hardwareHistory: (id: string) =>
    request<HardwareHealthResponse[]>(
      `/api/v1/endpoints/${id}/hardware-health`,
    ),

  hardwarePage: (
    p: {
      page?: number;
      size?: number;
      q?: string;
      band?: string;
    } = {},
  ) => {
    const qs = new URLSearchParams({
      page: String(p.page ?? 0),
      size: String(p.size ?? 25),
    });

    if (p.q?.trim()) {
      qs.set("q", p.q.trim());
    }

    if (p.band && p.band !== "ALL") {
      qs.set("band", p.band);
    }

    return request<PageResponse<HardwareListItem>>(
      `/api/v1/hardware-health/page?${qs}`,
    );
  },

  hardwareSummary: () =>
    request<HardwareSummary>("/api/v1/hardware-health/summary"),

  latestPostureBatch: (ids: string[]) =>
    ids.length === 0
      ? Promise.resolve([] as AssessmentResponse[])
      : request<AssessmentResponse[]>(
          `/api/v1/posture/latest/batch?ids=${ids.join(",")}`,
        ),

  endpointBriefs: async (ids: string[]) => {
    const out: Record<string, EndpointBrief> = {};

    for (let i = 0; i < ids.length; i += 100) {
      Object.assign(
        out,
        await request<Record<string, EndpointBrief>>(
          `/api/v1/endpoints/briefs?ids=${ids.slice(i, i + 100).join(",")}`,
        ),
      );
    }

    return out;
  },

  latestDiagnosticOrNull: (id: string) =>
    request<DiagnosticResponse | null>(
      `/api/v1/endpoints/${id}/diagnostics/latest`,
    ),

  diagnosticHistory: (id: string) =>
    request<DiagnosticResponse[]>(`/api/v1/endpoints/${id}/diagnostics`),

  latestSecurityOrNull: (id: string) =>
    request<SecurityIndicatorResponse | null>(
      `/api/v1/endpoints/${id}/security-indicators/latest`,
    ),

  securityHistory: (id: string) =>
    request<SecurityIndicatorResponse[]>(
      `/api/v1/endpoints/${id}/security-indicators`,
    ),

  sessionHistory: (id: string) =>
    request<SessionEvent[]>(`/api/v1/endpoints/${id}/sessions`),

  dashboardSummary: () =>
    request<DashboardSummary>("/api/v1/dashboard/summary"),

  dashboardTrend: (days = 7) =>
    request<TrendPoint[]>(`/api/v1/dashboard/trend?days=${days}`),

  dashboardCategories: () =>
    request<CategoryRate[]>("/api/v1/dashboard/categories"),

  listJobs: (limit = 500) =>
    request<JobResponse[]>(`/api/v1/jobs?limit=${limit}`),

  listJobsForEndpoint: (id: string, limit = 100) =>
    request<JobResponse[]>(`/api/v1/jobs/endpoint/${id}?limit=${limit}`),

  enqueueJob: (endpointId: string, jobType: JobType) =>
    request<JobResponse>("/api/v1/jobs", {
      method: "POST",
      body: JSON.stringify({
        endpointId,
        jobType,
      }),
    }),

  sharePosture: (endpointId: string) =>
    request<{
      success: boolean;
      detail: string;
    }>("/api/v1/ise/posture/share", {
      method: "POST",
      body: JSON.stringify({ endpointId }),
    }),

  restrict: (endpointId: string, policy?: string) =>
    request<{
      success: boolean;
      detail: string;
    }>("/api/v1/ise/enforcement/restrict", {
      method: "POST",
      body: JSON.stringify({
        endpointId,
        policy,
      }),
    }),

  clearRestriction: (endpointId: string) =>
    request<{
      success: boolean;
      detail: string;
    }>("/api/v1/ise/enforcement/clear", {
      method: "POST",
      body: JSON.stringify({ endpointId }),
    }),

  endpointsPage: (
    p: {
      page?: number;
      size?: number;
      q?: string;
      connected?: boolean;
      status?: string;
    } = {},
  ) => {
    const qs = new URLSearchParams({
      page: String(p.page ?? 0),
      size: String(p.size ?? 25),
    });

    if (p.q?.trim()) {
      qs.set("q", p.q.trim());
    }

    if (p.connected !== undefined) {
      qs.set("connected", String(p.connected));
    }

    if (p.status && p.status !== "ALL") {
      qs.set("status", p.status);
    }

    return request<PageResponse<EndpointListItem>>(
      `/api/v1/endpoints/page?${qs}`,
    );
  },

  endpointNames: (ids: string[]) =>
    ids.length === 0
      ? Promise.resolve({} as Record<string, string>)
      : request<Record<string, string>>(
          `/api/v1/endpoints/names?ids=${ids.join(",")}`,
        ),

  bulkEnqueue: (jobType: JobType) =>
    request<{ queued: number }>("/api/v1/jobs/bulk", {
      method: "POST",
      body: JSON.stringify({ jobType }),
    }),

  auditActions: (endpointId?: string, limit = 500) => {
    const qs = new URLSearchParams({
      limit: String(limit),
    });

    if (endpointId) {
      qs.set("endpointId", endpointId);
    }

    return request<IseActionAudit[]>(`/api/v1/audit/ise-actions?${qs}`);
  },

  iseActionsState: () =>
    request<IseActionState[]>("/api/v1/ise/actions/state"),

  iseStatus: () => request<IseStatus>("/api/v1/ise/status"),

  systemHealth: () => request<SystemHealth>("/api/v1/system/health"),

  listApplications: () => request<AppRow[]>("/api/v1/applications"),

  listPorts: () => request<PortRow[]>("/api/v1/ports"),

  policy: () => request<AppPolicy>("/api/v1/policy/apps"),

  policyHistory: () => request<AppPolicy[]>("/api/v1/policy/apps/history"),

  updatePolicy: (requiredApps: string[], blockedApps: string[]) =>
    request<AppPolicy>("/api/v1/policy/apps", {
      method: "PUT",
      body: JSON.stringify({
        requiredApps,
        blockedApps,
      }),
    }),

  users: () => request<UserView[]>("/api/v1/users"),

  createUser: (username: string, password: string, role: UserRole) =>
    request<UserView>("/api/v1/users", {
      method: "POST",
      body: JSON.stringify({
        username,
        password,
        role,
      }),
    }),

  updateUser: (
    id: string,
    patch: {
      role?: UserRole;
      enabled?: boolean;
    },
  ) =>
    request<UserView>(`/api/v1/users/${id}`, {
      method: "PATCH",
      body: JSON.stringify(patch),
    }),

  resetPassword: (id: string, password: string) =>
    request<null>(`/api/v1/users/${id}/password`, {
      method: "POST",
      body: JSON.stringify({ password }),
    }),

  deleteUser: (id: string) =>
    request<null>(`/api/v1/users/${id}`, {
      method: "DELETE",
    }),

  // ── Warranty ────────────────────────────────────────────────────────────────

  listWarranty: () => request<WarrantyView[]>("/api/v1/warranty"),

  uploadWarrantyCsv: (file: File): Promise<WarrantyUploadResult> => {
    const form = new FormData();

    form.append("file", file);

    return request<WarrantyUploadResult>("/api/v1/warranty/upload", {
      method: "POST",
      body: form,
    });
  },
};
