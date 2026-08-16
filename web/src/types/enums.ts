/**
 * Enums mirroring the backend entities. Each enum ships with display metadata
 * (label / description / badge tone) so the UI renders them consistently and
 * never repeats the mapping.
 */

export type Role = "SUPER_USER" | "READ_ONLY";

/** Which slug space a slug-suggestion lookup targets. */
export type SlugType = "PLATFORM" | "ORGANISATION";

export const ROLE_META: Record<Role, { label: string; description: string }> = {
  SUPER_USER: { label: "Super user", description: "Full management rights within the platform" },
  READ_ONLY: { label: "Read only", description: "Can authenticate and read platform data only" },
};

export type AuthType = "PASSWORD";

export const AUTH_TYPE_META: Record<AuthType, { label: string; description: string }> = {
  PASSWORD: { label: "Password", description: "Users authenticate with an identifier and password" },
};

export type UserFieldType = "STRING" | "NUMBER" | "BOOLEAN" | "DATE" | "EMAIL" | "LINK";

export const USER_FIELD_TYPE_META: Record<UserFieldType, { label: string; description: string }> = {
  STRING: { label: "Text", description: "Trimmed text (case-insensitive matching)" },
  NUMBER: { label: "Number", description: "Canonical decimal value (1.50 == 1.5)" },
  BOOLEAN: { label: "Boolean", description: "true / false" },
  DATE: { label: "Date", description: "ISO date (yyyy-MM-dd)" },
  EMAIL: { label: "Email", description: "Normalized email address" },
  LINK: { label: "Link", description: "http(s) URL" },
};

export type Permission =
  | "ORGANISATION_USER_READ"
  | "ORGANISATION_USER_CREATE"
  | "ORGANISATION_USER_UPDATE"
  | "ORGANISATION_USER_DELETE"
  | "ORGANISATION_USER_FIELD_READ"
  | "ORGANISATION_USER_FIELD_CREATE"
  | "ORGANISATION_USER_FIELD_UPDATE"
  | "ORGANISATION_USER_FIELD_DELETE";

export const ALL_PERMISSIONS: Permission[] = [
  "ORGANISATION_USER_READ",
  "ORGANISATION_USER_CREATE",
  "ORGANISATION_USER_UPDATE",
  "ORGANISATION_USER_DELETE",
  "ORGANISATION_USER_FIELD_READ",
  "ORGANISATION_USER_FIELD_CREATE",
  "ORGANISATION_USER_FIELD_UPDATE",
  "ORGANISATION_USER_FIELD_DELETE",
];

export const PERMISSION_META: Record<Permission, { label: string; description: string }> = {
  ORGANISATION_USER_READ: { label: "Read users", description: "View the organisation user directory" },
  ORGANISATION_USER_CREATE: { label: "Create users", description: "Add users to the organisation" },
  ORGANISATION_USER_UPDATE: { label: "Update users", description: "Edit users, roles and passwords" },
  ORGANISATION_USER_DELETE: { label: "Delete users", description: "Remove users from the organisation" },
  ORGANISATION_USER_FIELD_READ: { label: "Read user fields", description: "View organisation-defined user fields" },
  ORGANISATION_USER_FIELD_CREATE: { label: "Create user fields", description: "Define new user fields" },
  ORGANISATION_USER_FIELD_UPDATE: { label: "Update user fields", description: "Edit user field configuration" },
  ORGANISATION_USER_FIELD_DELETE: { label: "Delete user fields", description: "Remove user fields" },
};

/** Badge tones used by shared components, matching shadcn variants. */
export type BadgeTone = "default" | "secondary" | "success" | "destructive" | "warning" | "outline";

export const ROLE_TONE: Record<Role, BadgeTone> = { SUPER_USER: "warning", READ_ONLY: "secondary" };
export const USER_FIELD_TYPE_TONE: Record<UserFieldType, BadgeTone> = {
  STRING: "secondary",
  NUMBER: "default",
  BOOLEAN: "warning",
  DATE: "outline",
  EMAIL: "success",
  LINK: "default",
};

// ---------------------------------------------------------------------------
// Organisation clients
// ---------------------------------------------------------------------------

export type ClientType = "WEB" | "ANDROID" | "IOS" | "SERVER";

/** How the backend treats `requireAuthentication` for each client type. */
export type ClientAuthMode = "none" | "auth" | "optional";

export const CLIENT_TYPE_META: Record<ClientType, { label: string; description: string }> = {
  WEB: { label: "Web", description: "Browser app — signs users in, never authenticated itself" },
  ANDROID: { label: "Android", description: "Android app — can require a static token" },
  IOS: { label: "iOS", description: "iOS app — can require a static token" },
  SERVER: { label: "Server", description: "Backend service — always authenticated with a static token" },
};

/** Whether the type forces auth off / on, or lets the owner choose. */
export const CLIENT_TYPE_AUTH_MODE: Record<ClientType, ClientAuthMode> = {
  WEB: "none",
  ANDROID: "optional",
  IOS: "optional",
  SERVER: "auth",
};

export const CLIENT_TYPE_TONE: Record<ClientType, BadgeTone> = {
  WEB: "secondary",
  ANDROID: "default",
  IOS: "default",
  SERVER: "warning",
};
