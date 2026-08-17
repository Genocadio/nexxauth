/**
 * Central query-key definitions. Every page and mutation refers to these so
 * invalidations always match the keys queries were registered under.
 */
export const queryKeys = {
  me: ["auth", "me"] as const,
  platform: ["platform"] as const,
  platformUsers: ["platform-users"] as const,

  organisations: ["organisations"] as const,
  organisation: (organisationId: number) => ["organisation", organisationId] as const,
  orgUsers: (organisationId: number) => [...queryKeys.organisation(organisationId), "users"] as const,
  orgRoles: (organisationId: number) => [...queryKeys.organisation(organisationId), "roles"] as const,
  orgAuthConfig: (organisationId: number) => [...queryKeys.organisation(organisationId), "auth-config"] as const,
  orgSessionSettings: (organisationId: number) =>
    [...queryKeys.organisation(organisationId), "session-settings"] as const,
  orgUserFields: (organisationId: number) => [...queryKeys.organisation(organisationId), "user-fields"] as const,
  orgKeys: (organisationId: number) => [...queryKeys.organisation(organisationId), "keys"] as const,
  orgClients: (organisationId: number) => [...queryKeys.organisation(organisationId), "clients"] as const,
  docsContext: (platformSlug: string, organisationId: number) =>
    ["docs", platformSlug, organisationId] as const,
} as const;
