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
  CreateOrganisationClientLinkRequest,
  CreateOrganisationClientRequest,
  CreateOrganisationRequest,
  CreateOrganisationRoleRequest,
  CreateOrganisationUserFieldRequest,
  CreateOrganisationUserRequest,
  UpdateOrganisationAuthConfigRequest,
  UpdateOrganisationClientLinkRequest,
  UpdateOrganisationClientRequest,
  UpdateOrganisationRequest,
  UpdateOrganisationRoleRequest,
  UpdateOrganisationSessionSettingsRequest,
  UpdateOrganisationUserFieldRequest,
  UpdateOrganisationUserRequest,
} from "@/types/requests";
import type { OrganisationClientLinkResponse } from "@/types/api";

/** Organisation endpoints, addressed under the platform slug. */
export const organisationsApi = {
  list: (platformSlug: string) => get<OrganisationResponse[]>(endpoints.organisations(platformSlug).list, "platform"),
  create: (platformSlug: string, body: CreateOrganisationRequest) =>
    post<OrganisationResponse>(endpoints.organisations(platformSlug).list, body, "platform"),
  get: (platformSlug: string, organisationId: number) =>
    get<OrganisationResponse>(endpoints.organisations(platformSlug).one(organisationId), "platform"),
  update: (platformSlug: string, organisationId: number, body: UpdateOrganisationRequest) =>
    patch<OrganisationResponse>(
      endpoints.organisations(platformSlug).one(organisationId),
      body,
      "platform",
    ),
  remove: (platformSlug: string, organisationId: number) =>
    del<void>(endpoints.organisations(platformSlug).one(organisationId), "platform"),

  // -- users ---------------------------------------------------------------
  users: (platformSlug: string, organisationId: number) =>
    get<OrganisationUserResponse[]>(endpoints.organisations(platformSlug).users(organisationId), "platform"),
  createUser: (platformSlug: string, organisationId: number, body: CreateOrganisationUserRequest) =>
    post<OrganisationUserResponse>(
      endpoints.organisations(platformSlug).users(organisationId),
      body,
      "platform",
    ),
  updateUser: (
    platformSlug: string,
    organisationId: number,
    userId: number,
    body: UpdateOrganisationUserRequest,
  ) =>
    patch<OrganisationUserResponse>(
      endpoints.organisations(platformSlug).user(organisationId, userId),
      body,
      "platform",
    ),
  deleteUser: (platformSlug: string, organisationId: number, userId: number) =>
    del<void>(endpoints.organisations(platformSlug).user(organisationId, userId), "platform"),

  // -- roles ---------------------------------------------------------------
  roles: (platformSlug: string, organisationId: number) =>
    get<OrganisationRoleResponse[]>(endpoints.organisations(platformSlug).roles(organisationId), "platform"),
  createRole: (platformSlug: string, organisationId: number, body: CreateOrganisationRoleRequest) =>
    post<OrganisationRoleResponse>(
      endpoints.organisations(platformSlug).roles(organisationId),
      body,
      "platform",
    ),
  updateRole: (
    platformSlug: string,
    organisationId: number,
    roleId: number,
    body: UpdateOrganisationRoleRequest,
  ) =>
    patch<OrganisationRoleResponse>(
      endpoints.organisations(platformSlug).role(organisationId, roleId),
      body,
      "platform",
    ),
  deleteRole: (platformSlug: string, organisationId: number, roleId: number) =>
    del<void>(endpoints.organisations(platformSlug).role(organisationId, roleId), "platform"),

  // -- auth config & session settings --------------------------------------
  authConfig: (platformSlug: string, organisationId: number) =>
    get<OrganisationAuthConfigResponse>(
      endpoints.organisations(platformSlug).authConfig(organisationId),
      "platform",
    ),
  updateAuthConfig: (platformSlug: string, organisationId: number, body: UpdateOrganisationAuthConfigRequest) =>
    patch<OrganisationAuthConfigResponse>(
      endpoints.organisations(platformSlug).authConfig(organisationId),
      body,
      "platform",
    ),
  sessionSettings: (platformSlug: string, organisationId: number) =>
    get<OrganisationSessionSettingsResponse>(
      endpoints.organisations(platformSlug).sessionSettings(organisationId),
      "platform",
    ),
  updateSessionSettings: (
    platformSlug: string,
    organisationId: number,
    body: UpdateOrganisationSessionSettingsRequest,
  ) =>
    patch<OrganisationSessionSettingsResponse>(
      endpoints.organisations(platformSlug).sessionSettings(organisationId),
      body,
      "platform",
    ),

  // -- user fields ---------------------------------------------------------
  userFields: (platformSlug: string, organisationId: number) =>
    get<OrganisationUserFieldResponse[]>(
      endpoints.organisations(platformSlug).userFields(organisationId),
      "platform",
    ),
  createUserField: (platformSlug: string, organisationId: number, body: CreateOrganisationUserFieldRequest) =>
    post<OrganisationUserFieldResponse>(
      endpoints.organisations(platformSlug).userFields(organisationId),
      body,
      "platform",
    ),
  updateUserField: (
    platformSlug: string,
    organisationId: number,
    fieldId: number,
    body: UpdateOrganisationUserFieldRequest,
  ) =>
    patch<OrganisationUserFieldResponse>(
      endpoints.organisations(platformSlug).userField(organisationId, fieldId),
      body,
      "platform",
    ),
  deleteUserField: (platformSlug: string, organisationId: number, fieldId: number) =>
    del<void>(endpoints.organisations(platformSlug).userField(organisationId, fieldId), "platform"),

  // -- signing keys --------------------------------------------------------
  keys: (platformSlug: string, organisationId: number) =>
    get<OrganisationKeyResponse[]>(endpoints.organisations(platformSlug).keys(organisationId)),
  rotateKey: (platformSlug: string, organisationId: number) =>
    post<OrganisationKeyResponse>(endpoints.organisations(platformSlug).rotateKey(organisationId), undefined, "platform"),

  // -- clients -------------------------------------------------------------
  clients: (platformSlug: string, organisationId: number) =>
    get<OrganisationClientResponse[]>(endpoints.organisations(platformSlug).clients(organisationId), "platform"),
  getClient: (platformSlug: string, organisationId: number, clientKey: string) =>
    get<OrganisationClientResponse>(endpoints.organisations(platformSlug).client(organisationId, clientKey), "platform"),
  createClient: (platformSlug: string, organisationId: number, body: CreateOrganisationClientRequest) =>
    post<OrganisationClientResponse>(
      endpoints.organisations(platformSlug).clients(organisationId),
      body,
      "platform",
    ),
  updateClient: (
    platformSlug: string,
    organisationId: number,
    clientKey: string,
    body: UpdateOrganisationClientRequest,
  ) =>
    patch<OrganisationClientResponse>(
      endpoints.organisations(platformSlug).client(organisationId, clientKey),
      body,
      "platform",
    ),
  deleteClient: (platformSlug: string, organisationId: number, clientKey: string) =>
    del<void>(endpoints.organisations(platformSlug).client(organisationId, clientKey), "platform"),
  rotateClientToken: (platformSlug: string, organisationId: number, clientKey: string) =>
    post<OrganisationClientResponse>(
      endpoints.organisations(platformSlug).rotateClientToken(organisationId, clientKey),
      undefined,
      "platform",
    ),

  // -- client links --------------------------------------------------------
  clientLinks: (platformSlug: string, organisationId: number, clientKey: string) =>
    get<OrganisationClientLinkResponse[]>(
      endpoints.organisations(platformSlug).clientLinks(organisationId, clientKey),
      "platform",
    ),
  createClientLink: (
    platformSlug: string,
    organisationId: number,
    clientKey: string,
    body: CreateOrganisationClientLinkRequest,
  ) =>
    post<OrganisationClientLinkResponse>(
      endpoints.organisations(platformSlug).clientLinks(organisationId, clientKey),
      body,
      "platform",
    ),
  updateClientLink: (
    platformSlug: string,
    organisationId: number,
    clientKey: string,
    linkId: number,
    body: UpdateOrganisationClientLinkRequest,
  ) =>
    patch<OrganisationClientLinkResponse>(
      endpoints.organisations(platformSlug).clientLink(organisationId, clientKey, linkId),
      body,
      "platform",
    ),
  deleteClientLink: (
    platformSlug: string,
    organisationId: number,
    clientKey: string,
    linkId: number,
  ) =>
    del<void>(
      endpoints.organisations(platformSlug).clientLink(organisationId, clientKey, linkId),
      "platform",
    ),
};
