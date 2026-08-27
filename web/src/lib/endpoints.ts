import { API_BASE_URL } from "@/lib/constants";

/**
 * Single source of truth for API paths. Builders are parameterised by the
 * path segments the backend uses, so callers never interpolate URLs by hand.
 */
export const endpoints = {
  platformAuth: {
    register: `${API_BASE_URL}/auth/register`,
    login: `${API_BASE_URL}/auth/login`,
    refresh: `${API_BASE_URL}/auth/refresh`,
    logout: `${API_BASE_URL}/auth/logout`,
    me: `${API_BASE_URL}/auth/me`,
    changePassword: `${API_BASE_URL}/auth/me/password`,
  },

  platform: (slug: string) => ({
    get: `${API_BASE_URL}/${slug}`,
    users: `${API_BASE_URL}/${slug}/users`,
  }),

  user: (id: number) => `${API_BASE_URL}/users/${id}`,

  /** Slug availability/suggestions; type + name/slug are query params. */
  slugSuggestions: `${API_BASE_URL}/slug-suggestions`,

  organisations: (platformSlug: string) => ({
    list: `${API_BASE_URL}/${platformSlug}/organisations`,
    one: (organisationId: number) =>
      `${API_BASE_URL}/${platformSlug}/organisations/${organisationId}`,
    users: (organisationId: number) =>
      `${API_BASE_URL}/${platformSlug}/organisations/${organisationId}/users`,
    user: (organisationId: number, userId: number) =>
      `${API_BASE_URL}/${platformSlug}/organisations/${organisationId}/users/${userId}`,
    roles: (organisationId: number) =>
      `${API_BASE_URL}/${platformSlug}/organisations/${organisationId}/roles`,
    role: (organisationId: number, roleId: number) =>
      `${API_BASE_URL}/${platformSlug}/organisations/${organisationId}/roles/${roleId}`,
    authConfig: (organisationId: number) =>
      `${API_BASE_URL}/${platformSlug}/organisations/${organisationId}/auth-config`,
    sessionSettings: (organisationId: number) =>
      `${API_BASE_URL}/${platformSlug}/organisations/${organisationId}/session-settings`,
    userFields: (organisationId: number) =>
      `${API_BASE_URL}/${platformSlug}/organisations/${organisationId}/user-fields`,
    userField: (organisationId: number, fieldId: number) =>
      `${API_BASE_URL}/${platformSlug}/organisations/${organisationId}/user-fields/${fieldId}`,
    keys: (organisationId: number) =>
      `${API_BASE_URL}/${platformSlug}/organisations/${organisationId}/keys`,
    rotateKey: (organisationId: number) =>
      `${API_BASE_URL}/${platformSlug}/organisations/${organisationId}/keys/rotate`,
    clients: (organisationId: number) =>
      `${API_BASE_URL}/${platformSlug}/organisations/${organisationId}/clients`,
    client: (organisationId: number, clientKey: string) =>
      `${API_BASE_URL}/${platformSlug}/organisations/${organisationId}/clients/${clientKey}`,
    rotateClientToken: (organisationId: number, clientKey: string) =>
      `${API_BASE_URL}/${platformSlug}/organisations/${organisationId}/clients/${clientKey}/rotate-token`,
  }),

  orgAuth: (platformSlug: string) => ({
    register: `${API_BASE_URL}/${platformSlug}/auth/register`,
    login: `${API_BASE_URL}/${platformSlug}/auth/login`,
    refresh: `${API_BASE_URL}/${platformSlug}/auth/refresh`,
    logout: `${API_BASE_URL}/${platformSlug}/auth/logout`,
  }),

  docs: (platformSlug: string) => ({
    context: (organisationId: number) =>
      `${API_BASE_URL}/${platformSlug}/organisations/${organisationId}/docs/context`,
  }),

  logs: (platformSlug: string) => ({
    list: `${API_BASE_URL}/${platformSlug}/logs`,
    stream: `${API_BASE_URL}/${platformSlug}/logs/stream`,
  }),

  sessions: (platformSlug: string, organisationId: number) => ({
    list: `${API_BASE_URL}/${platformSlug}/organisations/${organisationId}/sessions`,
    revoke: (sessionId: string) =>
      `${API_BASE_URL}/${platformSlug}/organisations/${organisationId}/sessions/${sessionId}`,
    revokeAllForUser: (userId: number) =>
      `${API_BASE_URL}/${platformSlug}/organisations/${organisationId}/sessions?userId=${userId}`,
  }),
};
