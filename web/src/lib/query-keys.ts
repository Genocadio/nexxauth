/**
 * Central query-key definitions. Every page and mutation refers to these so
 * invalidations always match the keys queries were registered under.
 */
export const queryKeys = {
  me: ["auth", "me"] as const,
  platform: ["platform"] as const,
  platformUsers: ["platform-users"] as const,

  organisations: ["organisations"] as const,
  organisation: (organisationSlug: string) => ["organisation", organisationSlug] as const,
  orgUsers: (organisationSlug: string) => [...queryKeys.organisation(organisationSlug), "users"] as const,
  orgRoles: (organisationSlug: string) => [...queryKeys.organisation(organisationSlug), "roles"] as const,
  orgAuthConfig: (organisationSlug: string) => [...queryKeys.organisation(organisationSlug), "auth-config"] as const,
  orgSessionSettings: (organisationSlug: string) =>
    [...queryKeys.organisation(organisationSlug), "session-settings"] as const,
  orgUserFields: (organisationSlug: string) => [...queryKeys.organisation(organisationSlug), "user-fields"] as const,
  orgKeys: (organisationSlug: string) => [...queryKeys.organisation(organisationSlug), "keys"] as const,
  orgClients: (organisationSlug: string) => [...queryKeys.organisation(organisationSlug), "clients"] as const,
} as const;
