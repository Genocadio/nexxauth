import { z } from "zod";
import { PLATFORM_PASSWORD, SLUG_PATTERN } from "@/lib/constants";
import type { ClientType, Permission, UserFieldType } from "@/types/enums";

/**
 * Shared zod schemas. The rules mirror the backend's bean validation so the
 * UI gives the same answers the API would (names, lengths, slug patterns,
 * TTL bounds, cross-field rules).
 */

const slugMessage = "Lowercase letters, digits and single hyphens";

/** Optional slug field: empty string is accepted and treated as "absent". */
const optionalSlug = z.union([z.string().trim().regex(SLUG_PATTERN, slugMessage).max(100), z.literal("")]);

const optionalEmail = z.union([z.email("Enter a valid email").max(255), z.literal("")]);
const optionalLongText = z.union([z.string().trim().max(1000, "At most 1000 characters"), z.literal("")]);
const optionalPhone = z.union([z.string().trim().max(30, "At most 30 characters"), z.literal("")]);

const requiredName = (max: number, label = "This field") =>
  z.string().trim().min(1, `${label} is required`).max(max, `At most ${max} characters`);

// ---------------------------------------------------------------------------
// Platform auth
// ---------------------------------------------------------------------------

export const loginSchema = z.object({
  email: z.email("Enter a valid email"),
  password: z.string().min(1, "Password is required"),
});

export const registerSchema = z.object({
  firstName: requiredName(100, "First name"),
  lastName: requiredName(100, "Last name"),
  email: z.email("Enter a valid email"),
  password: z
    .string()
    .min(PLATFORM_PASSWORD.min, `At least ${PLATFORM_PASSWORD.min} characters`)
    .max(PLATFORM_PASSWORD.max, `At most ${PLATFORM_PASSWORD.max} characters`),
  phone: optionalPhone,
  platformName: requiredName(120, "Platform name"),
  platformSlug: optionalSlug,
});

export const changePasswordSchema = z.object({
  currentPassword: z.string().min(1, "Current password is required"),
  newPassword: z
    .string()
    .min(PLATFORM_PASSWORD.min, `At least ${PLATFORM_PASSWORD.min} characters`)
    .max(PLATFORM_PASSWORD.max, `At most ${PLATFORM_PASSWORD.max} characters`),
});

export const updateProfileSchema = z.object({
  firstName: requiredName(100, "First name"),
  lastName: requiredName(100, "Last name"),
  phone: optionalPhone,
});

// ---------------------------------------------------------------------------
// Platform users
// ---------------------------------------------------------------------------

/**
 * Form shape shared by the add/edit platform user dialog. Email and password
 * are only required when creating; `enabled` only applies when editing — the
 * dialog enforces those rules at submit time.
 */
export const platformUserFormSchema = z.object({
  firstName: requiredName(100, "First name"),
  lastName: requiredName(100, "Last name"),
  email: z.union([z.email("Enter a valid email"), z.literal("")]),
  password: z.union([
    z.string().min(PLATFORM_PASSWORD.min, `At least ${PLATFORM_PASSWORD.min} characters`).max(PLATFORM_PASSWORD.max, `At most ${PLATFORM_PASSWORD.max} characters`),
    z.literal(""),
  ]),
  phone: optionalPhone,
  role: z.enum(["SUPER_USER", "READ_ONLY"]),
  enabled: z.boolean(),
});

// ---------------------------------------------------------------------------
// Organisations
// ---------------------------------------------------------------------------

export const createOrganisationSchema = z.object({
  name: requiredName(200, "Organisation name"),
  slug: optionalSlug,
  description: optionalLongText,
});

export const updateOrganisationSchema = z.object({
  name: z.union([z.string().trim().min(1).max(200), z.literal("")]),
  description: optionalLongText,
  useEmailAsUsername: z.boolean().optional(),
});

// ---------------------------------------------------------------------------
// Organisation auth (org user portal)
// ---------------------------------------------------------------------------

export const orgLoginSchema = z.object({
  identifier: z.string().trim().min(1, "Identifier is required").max(255),
  password: z.string().min(1, "Password is required"),
});

// ---------------------------------------------------------------------------
// Organisation users
// ---------------------------------------------------------------------------

/**
 * Form shape shared by the create/edit org user dialog. `enabled` is only
 * submitted when editing; the dialog branches at submit time.
 */
export const orgUserFormSchema = z.object({
  firstName: requiredName(100, "First name"),
  lastName: requiredName(100, "Last name"),
  username: z.union([z.string().trim().max(100), z.literal("")]),
  email: optionalEmail,
  enabled: z.boolean(),
  roleIds: z.array(z.number().int()),
  password: z.union([z.string().max(72, "At most 72 characters"), z.literal("")]),
});

// ---------------------------------------------------------------------------
// Organisation roles
// ---------------------------------------------------------------------------

const permissionEnum = z.enum([
  "ORGANISATION_USER_READ",
  "ORGANISATION_USER_CREATE",
  "ORGANISATION_USER_UPDATE",
  "ORGANISATION_USER_DELETE",
  "ORGANISATION_USER_FIELD_READ",
  "ORGANISATION_USER_FIELD_CREATE",
  "ORGANISATION_USER_FIELD_UPDATE",
  "ORGANISATION_USER_FIELD_DELETE",
]);

export const roleSchema = z.object({
  name: requiredName(100, "Role name"),
  permissions: z.array(permissionEnum),
});

// ---------------------------------------------------------------------------
// Org auth config & session settings
// ---------------------------------------------------------------------------

export const authConfigSchema = z
  .object({
    authType: z.enum(["PASSWORD"]),
    passwordMinLength: z.number().int().min(1).max(72),
    passwordMaxLength: z.number().int().min(1).max(72),
    passwordExpirationDays: z.number().int().min(0).max(3650),
    passwordHistoryCount: z.number().int().min(0).max(50),
  })
  .refine((v) => v.passwordMinLength <= v.passwordMaxLength, {
    message: "Minimum length cannot exceed maximum length",
    path: ["passwordMinLength"],
  });

export const sessionSettingsSchema = z
  .object({
    accessTokenTtlSeconds: z.number().int().min(60).max(86400),
    refreshTokenTtlSeconds: z.number().int().min(300).max(31536000),
    maxSessionsPerUser: z.number().int().min(1).max(100),
  })
  .refine((v) => v.refreshTokenTtlSeconds > v.accessTokenTtlSeconds, {
    message: "Refresh token lifetime must outlive the access token",
    path: ["refreshTokenTtlSeconds"],
  });

// ---------------------------------------------------------------------------
// Organisation user fields
// ---------------------------------------------------------------------------

export const userFieldSchema = z.object({
  key: z
    .string()
    .trim()
    .regex(SLUG_PATTERN, slugMessage)
    .max(100, "At most 100 characters"),
  label: requiredName(100, "Label"),
  fieldType: z.enum(["STRING", "NUMBER", "BOOLEAN", "DATE"] as const satisfies readonly UserFieldType[]),
  loginEnabled: z.boolean().optional(),
});

export const updateUserFieldSchema = z.object({
  label: requiredName(100, "Label"),
  fieldType: z.enum(["STRING", "NUMBER", "BOOLEAN", "DATE"] as const),
  loginEnabled: z.boolean().optional(),
});

// ---------------------------------------------------------------------------
// Organisation clients
// ---------------------------------------------------------------------------

/**
 * Form shape shared by the create/edit client dialog. Allowed origins are
 * entered one per line; the dialog splits them into a list on submit.
 */
export const orgClientFormSchema = z.object({
  name: requiredName(100, "Name"),
  type: z.enum(["WEB", "ANDROID", "IOS", "SERVER"] as const satisfies readonly ClientType[]),
  requireAuthentication: z.boolean(),
  allowedOrigins: z.string(),
  enabled: z.boolean(),
});

export type PermissionsInput = Permission[];
