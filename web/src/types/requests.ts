/**
 * Request payload types — mirrors of the backend request DTO records under
 * com.nexxserve.nexxauth.dto.request. Optional fields are omitted from the
 * JSON body (JSON.stringify drops undefined) so the backend treats them as
 * "unchanged", which is the PATCH semantics every partial update relies on.
 */

import type { AuthType, ClientType, Permission, Role, UserFieldType } from "@/types/enums";

// ---------------------------------------------------------------------------
// Platform auth
// ---------------------------------------------------------------------------

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  phone?: string;
  platformName: string;
  /** Derived from the platform name when omitted; immutable afterwards. */
  platformSlug?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface LogoutRequest {
  refreshToken: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface UpdateProfileRequest {
  firstName?: string;
  lastName?: string;
  phone?: string;
}

// ---------------------------------------------------------------------------
// Platform & users
// ---------------------------------------------------------------------------

export interface AddPlatformUserRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  phone?: string;
  role?: Role;
}

export interface UpdateUserRequest {
  firstName?: string;
  lastName?: string;
  phone?: string;
  role?: Role;
  enabled?: boolean;
}

// ---------------------------------------------------------------------------
// Organisations
// ---------------------------------------------------------------------------

export interface CreateOrganisationRequest {
  name: string;
  slug?: string;
  description?: string;
}

export interface UpdateOrganisationRequest {
  name?: string;
  slug?: string;
  description?: string;
  /** Legacy switch: true = email required + login identifier, username not. */
  useEmailAsUsername?: boolean;
  emailRequired?: boolean;
  usernameRequired?: boolean;
  phoneRequired?: boolean;
  emailCanLogin?: boolean;
  usernameCanLogin?: boolean;
  phoneCanLogin?: boolean;
  /** Onboarding wizard progress: 1..7 = step, 8 = complete. */
  onboardingStep?: number;
}

// ---------------------------------------------------------------------------
// Organisation auth
// ---------------------------------------------------------------------------

export interface OrgRegisterRequest {
  /** Only required when the request carries no X-Client-Id header. */
  organisationId?: number;
  username?: string;
  email?: string;
  phone?: string;
  password?: string;
  firstName: string;
  lastName: string;
  metadata?: Record<string, string>;
}

export type OrgIdentifierType = "USERNAME" | "EMAIL" | "PHONE";

export interface OrgLoginRequest {
  /** Only required when the request carries no X-Client-Id header. */
  organisationId?: number;
  identifier: string;
  /** What kind of identifier is being sent; omit for auto-detection. */
  identifierType?: OrgIdentifierType;
  /** Defaults to PASSWORD; future methods (passkey, OTP, ...) extend this. */
  authType?: "PASSWORD";
  /** Required when authType is PASSWORD (the only method today). */
  password?: string;
}

// ---------------------------------------------------------------------------
// Organisation users
// ---------------------------------------------------------------------------

export interface CreateOrganisationUserRequest {
  firstName: string;
  lastName: string;
  username?: string;
  email?: string;
  phone?: string;
  roleIds?: number[];
  /** Omit to create the user without auth (cannot log in until set). */
  password?: string;
  metadata?: Record<string, string>;
}

export interface UpdateOrganisationUserRequest {
  firstName?: string;
  lastName?: string;
  /** Pass "" to clear; omitted to keep. */
  username?: string;
  /** Pass "" to clear; omitted to keep. */
  email?: string;
  /** Pass "" to clear; omitted to keep. */
  phone?: string;
  enabled?: boolean;
  /** Replaces the whole role set; [] clears all roles. */
  roleIds?: number[];
  /** Resets the password; "" clears auth and disables login. */
  password?: string;
  metadata?: Record<string, string>;
}

// ---------------------------------------------------------------------------
// Organisation roles
// ---------------------------------------------------------------------------

export interface CreateOrganisationRoleRequest {
  name: string;
  permissions?: Permission[];
}

export interface UpdateOrganisationRoleRequest {
  name?: string;
  permissions?: Permission[];
}

// ---------------------------------------------------------------------------
// Organisation auth config & session settings
// ---------------------------------------------------------------------------

export interface UpdateOrganisationAuthConfigRequest {
  authType?: AuthType;
  /** When false, password authentication is disabled for the org. */
  passwordEnabled?: boolean;
  passwordMinLength?: number;
  passwordMaxLength?: number;
  passwordExpirationDays?: number;
  passwordHistoryCount?: number;
}

export interface UpdateOrganisationSessionSettingsRequest {
  accessTokenTtlSeconds?: number;
  refreshTokenTtlSeconds?: number;
  maxSessionsPerUser?: number;
}

// ---------------------------------------------------------------------------
// Organisation user fields
// ---------------------------------------------------------------------------

export interface CreateOrganisationUserFieldRequest {
  key: string;
  fieldType: UserFieldType;
  loginEnabled?: boolean;
  /** When true, every user must have a value for this field. */
  required?: boolean;
}

export interface UpdateOrganisationUserFieldRequest {
  fieldType?: UserFieldType;
  loginEnabled?: boolean;
  required?: boolean;
}

// ---------------------------------------------------------------------------
// Organisation clients
// ---------------------------------------------------------------------------

export interface CreateOrganisationClientRequest {
  name: string;
  type: ClientType;
  requireAuthentication?: boolean;
  allowedOrigins?: string[];
  enabled?: boolean;
  settings?: Record<string, string>;
}

export interface UpdateOrganisationClientRequest {
  name?: string;
  requireAuthentication?: boolean;
  allowedOrigins?: string[];
  enabled?: boolean;
  settings?: Record<string, string>;
}
