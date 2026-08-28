import { store } from "@/store/store";
import { clearPlatformSession, setPlatformSession } from "@/store/authSlice";
import { API_BASE_URL } from "@/lib/constants";
import type { AuthResponse } from "@/types/api";
import { ApiError, type ErrorResponse } from "@/types/errors";
import { queryClient } from "@/lib/query-client";

/** Guard so multiple concurrent 401s only redirect once. */
let isRedirectingToLogin = false;

function forceLogout() {
  if (isRedirectingToLogin) return;
  isRedirectingToLogin = true;
  store.dispatch(clearPlatformSession());
  queryClient.clear();
  // Use replace so the back button doesn't land on a stale protected page.
  window.location.replace("/login");
}

/**
 * Which session a request authenticates with. Only the platform console uses
 * this client — org portal traffic is server-side (httpOnly cookies).
 */
export type AuthMode = "platform" | "none";

interface RequestOptions {
  method?: "GET" | "POST" | "PATCH" | "DELETE";
  body?: unknown;
  auth?: AuthMode;
  signal?: AbortSignal;
}

// ---------------------------------------------------------------------------
// Token / session access
// ---------------------------------------------------------------------------

function getAccessToken(): string | undefined {
  return store.getState().auth.platformSession?.accessToken;
}

function getRefreshToken(): string | undefined {
  return store.getState().auth.platformSession?.refreshToken;
}

// ---------------------------------------------------------------------------
// Token refresh (rotating; single in-flight request)
// ---------------------------------------------------------------------------

let platformRefresh: Promise<boolean> | null = null;

async function refreshSession(): Promise<boolean> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return false;

  try {
    const res = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) {
      store.dispatch(clearPlatformSession());
      return false;
    }
    const data = (await res.json()) as AuthResponse;
    store.dispatch(setPlatformSession({ ...data, loginAt: Date.now() }));
    return true;
  } catch {
    return false;
  }
}

function tryRefresh(): Promise<boolean> {
  platformRefresh ??= refreshSession().finally(() => {
    platformRefresh = null;
  });
  return platformRefresh;
}

// ---------------------------------------------------------------------------
// Error normalisation
// ---------------------------------------------------------------------------

async function toApiError(res: Response): Promise<ApiError> {
  let payload: Partial<ErrorResponse> = {};
  try {
    payload = (await res.json()) as ErrorResponse;
  } catch {
    // non-JSON error body — fall back to the status text below
  }
  return new ApiError(
    res.status,
    payload.message ?? res.statusText ?? "Request failed",
    payload.fieldErrors,
    payload.requestId,
  );
}

// ---------------------------------------------------------------------------
// Core request
// ---------------------------------------------------------------------------

export async function request<T>(
  path: string,
  { method = "GET", body, auth = "none", signal }: RequestOptions = {},
): Promise<T> {
  const buildHeaders = (token?: string): Record<string, string> => {
    const headers: Record<string, string> = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (token) headers.Authorization = `Bearer ${token}`;
    return headers;
  };

  const send = (token?: string) =>
    fetch(path, {
      method,
      headers: buildHeaders(token),
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal,
    });

  let res = await send(getAccessToken());

  // One 401 → attempt a silent refresh and retry once.
  if (res.status === 401 && auth === "platform") {
    const refreshed = await tryRefresh();
    if (refreshed) {
      res = await send(getAccessToken());
    }
  }

  // Still 401 after refresh attempt → force logout.
  if (res.status === 401 && auth === "platform") {
    forceLogout();
  }

  if (!res.ok) throw await toApiError(res);

  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const get = <T>(path: string, auth: AuthMode = "none", signal?: AbortSignal) =>
  request<T>(path, { method: "GET", auth, signal });

export const post = <T>(path: string, body?: unknown, auth: AuthMode = "none") =>
  request<T>(path, { method: "POST", body, auth });

export const patch = <T>(path: string, body?: unknown, auth: AuthMode = "none") =>
  request<T>(path, { method: "PATCH", body, auth });

export const del = <T>(path: string, auth: AuthMode = "none") =>
  request<T>(path, { method: "DELETE", auth });
