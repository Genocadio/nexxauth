import { del, get, patch, post } from "@/lib/api-client";
import { endpoints } from "@/lib/endpoints";
import type {
  OrganisationAuthConfigResponse,
  OrganisationClientResponse,
  OrganisationKeyResponse,
  OrganisationResponse,
  OrganisationRoleResponse,
  OrganisationSessionSettingsResponse,
  OrganisationUserFieldResponse,
  OrganisationUserResponse,
} from "@/types/api";
import type {
  CreateOrganisationClientRequest,
  CreateOrganisationRequest,
  CreateOrganisationRoleRequest,
  CreateOrganisationUserFieldRequest,
  CreateOrganisationUserRequest,
  UpdateOrganisationAuthConfigRequest,
  UpdateOrganisationClientRequest,
  UpdateOrganisationRequest,
  UpdateOrganisationRoleRequest,
  UpdateOrganisationSessionSettingsRequest,
  UpdateOrganisationUserFieldRequest,
  UpdateOrganisationUserRequest,
} from "@/types/requests";

/** Organisation endpoints, addressed under the platform slug. */
export const organisationsApi = {
  list: (platformSlug: string) => get<OrganisationResponse[]>(endpoints.organisations(platformSlug).list, "platform"),
  create: (platformSlug: string, body: CreateOrganisationRequest) =>
    post<OrganisationResponse>(endpoints.organisations(platformSlug).list, body, "platform"),
  get: (platformSlug: string, organisationSlug: string) =>
    get<OrganisationResponse>(endpoints.organisations(platformSlug).one(organisationSlug), "platform"),
  update: (platformSlug: string, organisationSlug: string, body: UpdateOrganisationRequest) =>
    patch<OrganisationResponse>(
      endpoints.organisations(platformSlug).one(organisationSlug),
      body,
      "platform",
    ),
  remove: (platformSlug: string, organisationSlug: string) =>
    del<void>(endpoints.organisations(platformSlug).one(organisationSlug), "platform"),

  // -- users ---------------------------------------------------------------
  // (The org portal's own /users/me is fetched server-side via the session
  // route handler — see lib/org-auth.ts — so there is no client counterpart.)
  users: (platformSlug: string, organisationSlug: string) =>
    get<OrganisationUserResponse[]>(endpoints.organisations(platformSlug).users(organisationSlug), "platform"),
  createUser: (platformSlug: string, organisationSlug: string, body: CreateOrganisationUserRequest) =>
    post<OrganisationUserResponse>(
      endpoints.organisations(platformSlug).users(organisationSlug),
      body,
      "platform",
    ),
  updateUser: (
    platformSlug: string,
    organisationSlug: string,
    userId: number,
    body: UpdateOrganisationUserRequest,
  ) =>
    patch<OrganisationUserResponse>(
      endpoints.organisations(platformSlug).user(organisationSlug, userId),
      body,
      "platform",
    ),
  deleteUser: (platformSlug: string, organisationSlug: string, userId: number) =>
    del<void>(endpoints.organisations(platformSlug).user(organisationSlug, userId), "platform"),

  // -- roles ---------------------------------------------------------------
  roles: (platformSlug: string, organisationSlug: string) =>
    get<OrganisationRoleResponse[]>(endpoints.organisations(platformSlug).roles(organisationSlug), "platform"),
  createRole: (platformSlug: string, organisationSlug: string, body: CreateOrganisationRoleRequest) =>
    post<OrganisationRoleResponse>(
      endpoints.organisations(platformSlug).roles(organisationSlug),
      body,
      "platform",
    ),
  updateRole: (
    platformSlug: string,
    organisationSlug: string,
    roleId: number,
    body: UpdateOrganisationRoleRequest,
  ) =>
    patch<OrganisationRoleResponse>(
      endpoints.organisations(platformSlug).role(organisationSlug, roleId),
      body,
      "platform",
    ),
  deleteRole: (platformSlug: string, organisationSlug: string, roleId: number) =>
    del<void>(endpoints.organisations(platformSlug).role(organisationSlug, roleId), "platform"),

  // -- auth config & session settings --------------------------------------
  authConfig: (platformSlug: string, organisationSlug: string) =>
    get<OrganisationAuthConfigResponse>(
      endpoints.organisations(platformSlug).authConfig(organisationSlug),
      "platform",
    ),
  updateAuthConfig: (platformSlug: string, organisationSlug: string, body: UpdateOrganisationAuthConfigRequest) =>
    patch<OrganisationAuthConfigResponse>(
      endpoints.organisations(platformSlug).authConfig(organisationSlug),
      body,
      "platform",
    ),
  sessionSettings: (platformSlug: string, organisationSlug: string) =>
    get<OrganisationSessionSettingsResponse>(
      endpoints.organisations(platformSlug).sessionSettings(organisationSlug),
      "platform",
    ),
  updateSessionSettings: (
    platformSlug: string,
    organisationSlug: string,
    body: UpdateOrganisationSessionSettingsRequest,
  ) =>
    patch<OrganisationSessionSettingsResponse>(
      endpoints.organisations(platformSlug).sessionSettings(organisationSlug),
      body,
      "platform",
    ),

  // -- user fields ---------------------------------------------------------
  userFields: (platformSlug: string, organisationSlug: string) =>
    get<OrganisationUserFieldResponse[]>(
      endpoints.organisations(platformSlug).userFields(organisationSlug),
      "platform",
    ),
  createUserField: (platformSlug: string, organisationSlug: string, body: CreateOrganisationUserFieldRequest) =>
    post<OrganisationUserFieldResponse>(
      endpoints.organisations(platformSlug).userFields(organisationSlug),
      body,
      "platform",
    ),
  updateUserField: (
    platformSlug: string,
    organisationSlug: string,
    fieldId: number,
    body: UpdateOrganisationUserFieldRequest,
  ) =>
    patch<OrganisationUserFieldResponse>(
      endpoints.organisations(platformSlug).userField(organisationSlug, fieldId),
      body,
      "platform",
    ),
  deleteUserField: (platformSlug: string, organisationSlug: string, fieldId: number) =>
    del<void>(endpoints.organisations(platformSlug).userField(organisationSlug, fieldId), "platform"),

  // -- signing keys --------------------------------------------------------
  keys: (platformSlug: string, organisationSlug: string) =>
    get<OrganisationKeyResponse[]>(endpoints.organisations(platformSlug).keys(organisationSlug)),
  rotateKey: (platformSlug: string, organisationSlug: string) =>
    post<OrganisationKeyResponse>(endpoints.organisations(platformSlug).rotateKey(organisationSlug), undefined, "platform"),

  // -- clients -------------------------------------------------------------
  clients: (platformSlug: string, organisationSlug: string) =>
    get<OrganisationClientResponse[]>(endpoints.organisations(platformSlug).clients(organisationSlug), "platform"),
  getClient: (platformSlug: string, organisationSlug: string, clientId: number) =>
    get<OrganisationClientResponse>(endpoints.organisations(platformSlug).client(organisationSlug, clientId), "platform"),
  createClient: (platformSlug: string, organisationSlug: string, body: CreateOrganisationClientRequest) =>
    post<OrganisationClientResponse>(
      endpoints.organisations(platformSlug).clients(organisationSlug),
      body,
      "platform",
    ),
  updateClient: (
    platformSlug: string,
    organisationSlug: string,
    clientId: number,
    body: UpdateOrganisationClientRequest,
  ) =>
    patch<OrganisationClientResponse>(
      endpoints.organisations(platformSlug).client(organisationSlug, clientId),
      body,
      "platform",
    ),
  deleteClient: (platformSlug: string, organisationSlug: string, clientId: number) =>
    del<void>(endpoints.organisations(platformSlug).client(organisationSlug, clientId), "platform"),
  rotateClientToken: (platformSlug: string, organisationSlug: string, clientId: number) =>
    post<OrganisationClientResponse>(
      endpoints.organisations(platformSlug).rotateClientToken(organisationSlug, clientId),
      undefined,
      "platform",
    ),
};
