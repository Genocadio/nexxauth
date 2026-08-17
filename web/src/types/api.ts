/**
 * Response types — exact mirrors of the backend DTO records under
 * com.nexxserve.nexxauth.dto.response. Keep in sync when the backend changes.
 */

import type { AuthType, ClientType, Permission, Role, SlugType, UserFieldType } from "@/types/enums";

/** ISO-8601 timestamp, as serialized by the backend. */
export type IsoDate = string;

export interface PlatformSummary {
  id: number;
  name: string;
  slug: string;
}

export interface PlatformResponse {
  id: number;
  name: string;
  slug: string;
  userCount: number;
  /** Public API base of this platform (e.g. https://auth.example.com/acme),
   * derived from the backend's BACKEND_PUBLIC_URL. Null when not configured. */
  apiBaseUrl: string | null;
  createdAt: IsoDate;
}

export interface PlatformUserResponse {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  role: Role;
  enabled: boolean;
  platform: PlatformSummary;
  createdAt: IsoDate;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: PlatformUserResponse;
}

export interface OrganisationResponse {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  emailRequired: boolean;
  usernameRequired: boolean;
  phoneRequired: boolean;
  emailCanLogin: boolean;
  usernameCanLogin: boolean;
  phoneCanLogin: boolean;
  /** Onboarding wizard progress: 1..7 = step, 8 = complete, null = not started. */
  onboardingStep: number | null;
  /** Backwards-compatible: true when email is the primary login identifier. */
  useEmailAsUsername: boolean;
  createdAt: IsoDate;
}

/** Slug availability + suggestions for a name/candidate. */
export interface SlugCandidate {
  slug: string;
  available: boolean;
}

export interface SlugSuggestionResponse {
  type: SlugType;
  candidate: SlugCandidate;
  suggestions: SlugCandidate[];
}

export interface OrganisationRoleResponse {
  id: number;
  name: string;
  permissions: Permission[];
  /** When true, new users of the org inherit this role automatically on register. */
  isDefault: boolean;
}

export interface OrganisationUserResponse {
  id: number;
  firstName: string;
  /** Optional: null when the user was created without a last name. */
  lastName: string | null;
  username: string | null;
  email: string | null;
  phone: string | null;
  enabled: boolean;
  /** The user's enabled auth methods (only PASSWORD exists today). Empty when
   * the user has no auth configured and cannot log in. */
  authTypes: AuthType[];
  /** The names of the roles the user holds — never ids, never permissions. */
  roles: string[];
  createdAt: IsoDate;
  /** Values of the org's configured user fields, keyed by field key. */
  metadata: Record<string, string> | null;
}

export interface OrgAuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: OrganisationUserResponse;
}

export interface OrganisationAuthConfigResponse {
  authType: AuthType;
  passwordEnabled: boolean;
  passwordMinLength: number;
  passwordMaxLength: number;
  passwordExpirationDays: number;
  passwordHistoryCount: number;
}

export interface OrganisationSessionSettingsResponse {
  accessTokenTtlSeconds: number;
  refreshTokenTtlSeconds: number;
  maxSessionsPerUser: number;
}

export interface OrganisationUserFieldResponse {
  id: number;
  key: string;
  fieldType: UserFieldType;
  loginEnabled: boolean;
  required: boolean;
  createdAt: IsoDate;
}

export interface OrganisationKeyResponse {
  kid: string;
  publicKey: string;
  active: boolean;
}

export interface OrganisationClientResponse {
  clientKey: string;
  name: string;
  type: ClientType;
  requireAuthentication: boolean;
  allowedOrigins: string[];
  enabled: boolean;
  settings: Record<string, string> | null;
  createdAt: IsoDate;
  /** Only present on create/rotate responses — the static token is shown once. */
  token?: string;
  /** Session overrides (null = using org defaults). */
  accessTokenTtlSeconds: number | null;
  refreshTokenTtlSeconds: number | null;
  maxSessionsPerUser: number | null;
}

/** Full display name of a person. */
export function fullName(user: Pick<PlatformUserResponse | OrganisationUserResponse, "firstName" | "lastName">): string {
  return [user.firstName, user.lastName].filter(Boolean).join(" ");
}
