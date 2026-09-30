/**
 * Response types — exact mirrors of the backend DTO records under
 * com.nexxserve.nexxauth.dto.response. Keep in sync when the backend changes.
 */

import type {
  AuthType,
  ClientType,
  Permission,
  Role,
  SlugType,
  UserFieldType,
  UserLoginMethod,
  VerificationChannel,
  VerificationDelivery,
} from "@/types/enums";

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

export interface OrganisationUserEmailResponse {
  id: number;
  email: string;
  isPrimary: boolean;
  verified: boolean;
  verifiedAt: IsoDate | null;
  createdAt: IsoDate;
}

export interface OrganisationUserPhoneResponse {
  id: number;
  phone: string;
  isPrimary: boolean;
  verified: boolean;
  verifiedAt: IsoDate | null;
  createdAt: IsoDate;
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
  emailVerified?: boolean;
  phoneVerified?: boolean;
  emails?: OrganisationUserEmailResponse[];
  phones?: OrganisationUserPhoneResponse[];
  requireEmailVerificationAtNextLogin?: boolean;
  requirePhoneVerificationAtNextLogin?: boolean;
  /** Which credential this user may sign in with. Chosen per user; separate
   * from `authTypes`, which reports what is actually usable right now. */
  loginMethod: UserLoginMethod;
  /** The credentials this user can actually use right now, derived rather
   * than stored. Empty when the user has no usable credential — a placeholder
   * created without a password cannot sign in until one is configured. */
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

/**
 * Acknowledgement that a verification value was sent. Carries no secret: the
 * code or link itself only ever reaches the user, through the notification
 * service. `accessToken` is the action token the *user's* app uses to complete
 * a password reset without re-sending their identifier.
 */
export interface VerificationRequestResponse {
  purpose: "EMAIL_VERIFICATION" | "PHONE_VERIFICATION" | "PASSWORD_RESET" | "LOGIN_OTP";
  channel: VerificationChannel;
  delivery: VerificationDelivery;
  identifier: string;
  expiresInSeconds: number;
  accessToken: string;
  tokenType: string;
}

export interface OrganisationAuthConfigResponse {
  authType: AuthType;
  passwordEnabled: boolean;
  passwordMinLength: number;
  passwordMaxLength: number;
  passwordExpirationDays: number;
  passwordHistoryCount: number;
  emailVerificationEnabled: boolean;
  phoneVerificationEnabled: boolean;
  passwordResetEnabled: boolean;
  otpLoginEnabled: boolean;
  twoFactorEnabled: boolean;
  verificationMode: "OTP" | "LINK";
  requireEmailVerificationOnRegister: boolean;
  requirePhoneVerificationOnRegister: boolean;
  /** False when the notification service (nexxbotify) is not configured:
   * email/phone verification, password reset, OTP login and 2FA are locked. */
  verificationServiceAvailable: boolean;
}

export interface EmailTemplateConfig {
  enabled: boolean;
  subject: string;
  body: string;
  html: string;
}

export interface SmsTemplateConfig {
  enabled: boolean;
  body: string;
}

export interface OrganisationNotificationTemplates {
  flowId: string;
  email: EmailTemplateConfig;
  sms: SmsTemplateConfig;
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

export interface OrganisationClientLinkResponse {
  id: number;
  origin: string;
  allowCors: boolean;
  limitSource: boolean;
  createdAt: IsoDate;
}

export interface OrganisationClientResponse {
  clientKey: string;
  name: string;
  type: ClientType;
  requireAuthentication: boolean;
  enabled: boolean;
  settings: Record<string, string> | null;
  createdAt: IsoDate;
  /** Only present on create/rotate responses — the static token is shown once. */
  token?: string;
  /** Session overrides (null = using org defaults). */
  accessTokenTtlSeconds: number | null;
  refreshTokenTtlSeconds: number | null;
  maxSessionsPerUser: number | null;
  /** Per-client login/register restrictions. */
  allowRegister: boolean;
  allowLogin: boolean;
  /** Set of allowed role names; empty means no restriction. */
  allowedRoles: string[];
  /** How allowedRoles is interpreted: NONE, ALLOWLIST, or BLOCKLIST. */
  roleRestrictionMode: "NONE" | "ALLOWLIST" | "BLOCKLIST";
  /** Per-link CORS and source-restriction settings. */
  links: OrganisationClientLinkResponse[];
}

/** Full display name of a person. */
export function fullName(user: Pick<PlatformUserResponse | OrganisationUserResponse, "firstName" | "lastName">): string {
  return [user.firstName, user.lastName].filter(Boolean).join(" ");
}

// ---------------------------------------------------------------------------
// Logs
// ---------------------------------------------------------------------------

export type LogLevel = "INFO" | "WARN" | "ERROR";

export type LogCategory = "AUTH" | "USER_MANAGEMENT" | "ORG_MANAGEMENT" | "CONFIG" | "SECURITY";

export interface LogEntryResponse {
  id: number;
  organisationId: number | null;
  organisationSlug: string | null;
  level: LogLevel;
  category: LogCategory;
  eventType: string;
  message: string;
  actor: string | null;
  ip: string | null;
  requestId: string | null;
  detail: string | null;
  clientKey: string | null;
  domain: string | null;
  createdAt: IsoDate;
}

export interface LogPage {
  content: LogEntryResponse[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

// ---------------------------------------------------------------------------
// Sessions
// ---------------------------------------------------------------------------

export interface OrganisationSessionResponse {
  sessionId: string;
  userId: number;
  userIdentifier: string;
  ipAddress: string | null;
  userAgent: string | null;
  clientKey: string | null;
  clientName: string | null;
  clientType: string | null;
  hostname: string | null;
  createdAt: IsoDate;
  lastActivityAt: IsoDate;
  expiresAt: IsoDate;
  active: boolean;
  tokenCount: number;
}

export interface SessionTimelineEvent {
  createdAt: IsoDate;
  expiresAt: IsoDate;
  revokedAt: IsoDate | null;
  evictedAt: IsoDate | null;
  active: boolean;
  clientKey: string | null;
  hostname: string | null;
}
