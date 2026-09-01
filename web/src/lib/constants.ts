/**
 * Base URL of the Nexxauth API. Defaults to the same-origin path that
 * next.config.ts proxies to the backend (no CORS needed). Set
 * NEXT_PUBLIC_API_BASE_URL to an absolute URL to call the API directly
 * (the backend must then allow CORS for this origin).
 */
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api/v1";

/** localStorage keys for persisted sessions (platform console only — the org
 * portal session lives in httpOnly cookies, server-side). */
export const STORAGE_KEYS = {
  platformSession: "nexxauth.platform.session",
} as const;

/** Slug/key pattern shared by platforms, organisations and user-field keys. */
export const SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

/** Platform-level password rules (fixed by the backend). */
export const PLATFORM_PASSWORD = { min: 8, max: 72 } as const;

/** Default organisation auth config (mirrors the backend defaults). */
export const DEFAULT_AUTH_CONFIG = {
  authType: "PASSWORD",
  passwordMinLength: 8,
  passwordMaxLength: 72,
  passwordExpirationDays: 0,
  passwordHistoryCount: 0,
} as const;

/** Default organisation session settings (mirrors the backend defaults). */
export const DEFAULT_SESSION_SETTINGS = {
  accessTokenTtlSeconds: 900,
  refreshTokenTtlSeconds: 604800,
  maxSessionsPerUser: 5,
} as const;

/** Units offered by the session-settings duration pickers. */
export const DURATION_UNITS = [
  { value: "minutes", label: "minutes", seconds: 60 },
  { value: "hours", label: "hours", seconds: 3600 },
  { value: "days", label: "days", seconds: 86400 },
] as const;

export type DurationUnit = (typeof DURATION_UNITS)[number]["value"];

/** Seconds for a given value + unit (defaults to minutes). */
export function toSeconds(value: number, unit: DurationUnit): number {
  const secs = DURATION_UNITS.find((u) => u.value === unit)?.seconds ?? 60;
  return value * secs;
}

/**
 * Split a seconds duration into a friendly value + unit for the picker — the
 * largest unit that divides it evenly (e.g. 604800 -> 7 days, 900 -> 15 min).
 */
export function decomposeDuration(totalSeconds: number): { value: number; unit: DurationUnit } {
  if (totalSeconds <= 0) return { value: 1, unit: "minutes" };
  for (let i = DURATION_UNITS.length - 1; i >= 0; i--) {
    const u = DURATION_UNITS[i];
    if (totalSeconds % u.seconds === 0) {
      return { value: totalSeconds / u.seconds, unit: u.value };
    }
  }
  return { value: Math.round(totalSeconds / 60), unit: "minutes" };
}

/** Human-friendly formatting of a seconds duration (e.g. "15 min"). */
export function formatDuration(totalSeconds: number): string {
  if (totalSeconds < 60) return `${totalSeconds}s`;
  if (totalSeconds < 3600) {
    const m = Math.round(totalSeconds / 60);
    return `${m} min`;
  }
  if (totalSeconds < 86400) {
    const h = Math.round(totalSeconds / 3600);
    return `${h} hr${h === 1 ? "" : "s"}`;
  }
  const d = Math.round(totalSeconds / 86400);
  return `${d} day${d === 1 ? "" : "s"}`;
}

/** Human-friendly date formatting for created-at timestamps. */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}
