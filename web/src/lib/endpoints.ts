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
    get: `${API_BASE_URL}/platforms/${slug}`,
    users: `${API_BASE_URL}/platforms/${slug}/users`,
  }),

  user: (id: number) => `${API_BASE_URL}/users/${id}`,

  /** Slug availability/suggestions; type + name/slug are query params. */
  slugSuggestions: `${API_BASE_URL}/slug-suggestions`,

  organisations: (platformSlug: string) => ({
    list: `${API_BASE_URL}/platforms/${platformSlug}/organisations`,
    one: (organisationSlug: string) =>
      `${API_BASE_URL}/platforms/${platformSlug}/organisations/${organisationSlug}`,
    users: (organisationSlug: string) =>
      `${API_BASE_URL}/platforms/${platformSlug}/organisations/${organisationSlug}/users`,
    user: (organisationSlug: string, userId: number) =>
      `${API_BASE_URL}/platforms/${platformSlug}/organisations/${organisationSlug}/users/${userId}`,
    roles: (organisationSlug: string) =>
      `${API_BASE_URL}/platforms/${platformSlug}/organisations/${organisationSlug}/roles`,
    role: (organisationSlug: string, roleId: number) =>
      `${API_BASE_URL}/platforms/${platformSlug}/organisations/${organisationSlug}/roles/${roleId}`,
    authConfig: (organisationSlug: string) =>
      `${API_BASE_URL}/platforms/${platformSlug}/organisations/${organisationSlug}/auth-config`,
    sessionSettings: (organisationSlug: string) =>
      `${API_BASE_URL}/platforms/${platformSlug}/organisations/${organisationSlug}/session-settings`,
    userFields: (organisationSlug: string) =>
      `${API_BASE_URL}/platforms/${platformSlug}/organisations/${organisationSlug}/user-fields`,
    userField: (organisationSlug: string, fieldId: number) =>
      `${API_BASE_URL}/platforms/${platformSlug}/organisations/${organisationSlug}/user-fields/${fieldId}`,
    keys: (organisationSlug: string) =>
      `${API_BASE_URL}/platforms/${platformSlug}/organisations/${organisationSlug}/keys`,
    rotateKey: (organisationSlug: string) =>
      `${API_BASE_URL}/platforms/${platformSlug}/organisations/${organisationSlug}/keys/rotate`,
    clients: (organisationSlug: string) =>
      `${API_BASE_URL}/platforms/${platformSlug}/organisations/${organisationSlug}/clients`,
    client: (organisationSlug: string, clientId: number) =>
      `${API_BASE_URL}/platforms/${platformSlug}/organisations/${organisationSlug}/clients/${clientId}`,
    rotateClientToken: (organisationSlug: string, clientId: number) =>
      `${API_BASE_URL}/platforms/${platformSlug}/organisations/${organisationSlug}/clients/${clientId}/rotate-token`,
  }),

  orgAuth: (platformSlug: string) => ({
    register: `${API_BASE_URL}/platforms/${platformSlug}/auth/register`,
    login: `${API_BASE_URL}/platforms/${platformSlug}/auth/login`,
    refresh: `${API_BASE_URL}/platforms/${platformSlug}/auth/refresh`,
    logout: `${API_BASE_URL}/platforms/${platformSlug}/auth/logout`,
  }),
};
