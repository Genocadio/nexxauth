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
  useEmailAsUsername?: boolean;
}

// ---------------------------------------------------------------------------
// Organisation auth
// ---------------------------------------------------------------------------

export interface OrgRegisterRequest {
  organisationId: number;
  identifier: string;
  password: string;
  firstName: string;
  lastName: string;
  metadata?: Record<string, string>;
}

export interface OrgLoginRequest {
  organisationId: number;
  identifier: string;
  password: string;
}

// ---------------------------------------------------------------------------
// Organisation users
// ---------------------------------------------------------------------------

export interface CreateOrganisationUserRequest {
  firstName: string;
  lastName: string;
  username?: string;
  email?: string;
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
  label: string;
  fieldType: UserFieldType;
  loginEnabled?: boolean;
}

export interface UpdateOrganisationUserFieldRequest {
  label?: string;
  fieldType?: UserFieldType;
  loginEnabled?: boolean;
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
