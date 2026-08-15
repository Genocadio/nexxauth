"use client";

import {
  useMutation,
  useQueryClient,
  type QueryClient,
  type UseMutationResult,
} from "@tanstack/react-query";
import { toast } from "sonner";
import { organisationsApi } from "@/api/organisations";
import { platformApi } from "@/api/platform";
import { queryKeys } from "@/lib/query-keys";
import { getErrorMessage } from "@/types/errors";
import type {
  OrganisationAuthConfigResponse,
  OrganisationClientResponse,
  OrganisationKeyResponse,
  OrganisationResponse,
  OrganisationRoleResponse,
  OrganisationSessionSettingsResponse,
  OrganisationUserFieldResponse,
  OrganisationUserResponse,
  PlatformUserResponse,
} from "@/types/api";
import type {
  AddPlatformUserRequest,
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
  UpdateUserRequest,
} from "@/types/requests";

interface ApiMutationOptions<TData, TVars> {
  mutationFn: (vars: TVars) => Promise<TData>;
  /** Query keys to invalidate on success. */
  invalidate?: ((qc: QueryClient, vars: TVars) => void)[];
  successMessage?: string;
  onSuccess?: (data: TData, vars: TVars) => void;
}

/**
 * Wraps React Query mutations with the app's conventions: unified error
 * toasts and query invalidation. Every mutation hook below is built on this,
 * so behaviour (and the error message format) never drifts.
 */
function useApiMutation<TData, TVars>(options: ApiMutationOptions<TData, TVars>): UseMutationResult<TData, Error, TVars> {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: options.mutationFn,
    onSuccess: (data, vars) => {
      options.invalidate?.forEach((invalidate) => invalidate(qc, vars));
      if (options.successMessage) toast.success(options.successMessage);
      options.onSuccess?.(data, vars);
    },
    onError: (error) => {
      toast.error(getErrorMessage(error));
    },
  });
}

// ---------------------------------------------------------------------------
// Platform users
// ---------------------------------------------------------------------------

export function useCreatePlatformUser(platformSlug: string) {
  return useApiMutation<PlatformUserResponse, AddPlatformUserRequest>({
    mutationFn: (body) => platformApi.addUser(platformSlug, body),
    invalidate: [
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.platformUsers }),
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.platform }),
    ],
    successMessage: "Platform user added",
  });
}

export function useUpdatePlatformUser() {
  return useApiMutation<PlatformUserResponse, { userId: number; body: UpdateUserRequest }>({
    mutationFn: ({ userId, body }) => platformApi.updateUser(userId, body),
    invalidate: [
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.platformUsers }),
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.platform }),
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.me }),
    ],
    successMessage: "User updated",
  });
}

// ---------------------------------------------------------------------------
// Organisations (platform level)
// ---------------------------------------------------------------------------

export function useCreateOrganisation(platformSlug: string) {
  return useApiMutation<OrganisationResponse, CreateOrganisationRequest>({
    mutationFn: (body) => organisationsApi.create(platformSlug, body),
    invalidate: [
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.organisations }),
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.platform }),
    ],
    successMessage: "Organisation created",
  });
}

export function useUpdateOrganisation(platformSlug: string, organisationSlug: string) {
  return useApiMutation<OrganisationResponse, UpdateOrganisationRequest>({
    mutationFn: (body) => organisationsApi.update(platformSlug, organisationSlug, body),
    invalidate: [
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.organisations }),
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.organisation(organisationSlug) }),
    ],
    successMessage: "Organisation updated",
  });
}

export function useDeleteOrganisation(platformSlug: string, organisationSlug: string) {
  return useApiMutation<void, void>({
    mutationFn: () => organisationsApi.remove(platformSlug, organisationSlug),
    invalidate: [
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.organisations }),
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.platform }),
      (qc) => qc.removeQueries({ queryKey: queryKeys.organisation(organisationSlug) }),
    ],
    successMessage: "Organisation deleted",
  });
}

// ---------------------------------------------------------------------------
// Organisation users
// ---------------------------------------------------------------------------

export function useCreateOrgUser(platformSlug: string, organisationSlug: string) {
  return useApiMutation<OrganisationUserResponse, CreateOrganisationUserRequest>({
    mutationFn: (body) => organisationsApi.createUser(platformSlug, organisationSlug, body),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgUsers(organisationSlug) })],
    successMessage: "User created",
  });
}

export function useUpdateOrgUser(platformSlug: string, organisationSlug: string) {
  return useApiMutation<OrganisationUserResponse, { userId: number; body: UpdateOrganisationUserRequest }>({
    mutationFn: ({ userId, body }) => organisationsApi.updateUser(platformSlug, organisationSlug, userId, body),
    invalidate: [
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.orgUsers(organisationSlug) }),
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.orgRoles(organisationSlug) }),
    ],
    successMessage: "User updated",
  });
}

export function useDeleteOrgUser(platformSlug: string, organisationSlug: string) {
  return useApiMutation<void, number>({
    mutationFn: (userId) => organisationsApi.deleteUser(platformSlug, organisationSlug, userId),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgUsers(organisationSlug) })],
    successMessage: "User deleted",
  });
}

// ---------------------------------------------------------------------------
// Organisation roles
// ---------------------------------------------------------------------------

export function useCreateOrgRole(platformSlug: string, organisationSlug: string) {
  return useApiMutation<OrganisationRoleResponse, CreateOrganisationRoleRequest>({
    mutationFn: (body) => organisationsApi.createRole(platformSlug, organisationSlug, body),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgRoles(organisationSlug) })],
    successMessage: "Role created",
  });
}

export function useUpdateOrgRole(platformSlug: string, organisationSlug: string) {
  return useApiMutation<OrganisationRoleResponse, { roleId: number; body: UpdateOrganisationRoleRequest }>({
    mutationFn: ({ roleId, body }) => organisationsApi.updateRole(platformSlug, organisationSlug, roleId, body),
    invalidate: [
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.orgRoles(organisationSlug) }),
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.orgUsers(organisationSlug) }),
    ],
    successMessage: "Role updated",
  });
}

export function useDeleteOrgRole(platformSlug: string, organisationSlug: string) {
  return useApiMutation<void, number>({
    mutationFn: (roleId) => organisationsApi.deleteRole(platformSlug, organisationSlug, roleId),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgRoles(organisationSlug) })],
    successMessage: "Role deleted",
  });
}

// ---------------------------------------------------------------------------
// Org auth config & session settings
// ---------------------------------------------------------------------------

export function useUpdateOrgAuthConfig(platformSlug: string, organisationSlug: string) {
  return useApiMutation<OrganisationAuthConfigResponse, UpdateOrganisationAuthConfigRequest>({
    mutationFn: (body) => organisationsApi.updateAuthConfig(platformSlug, organisationSlug, body),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgAuthConfig(organisationSlug) })],
    successMessage: "Authentication settings saved",
  });
}

export function useUpdateOrgSessionSettings(platformSlug: string, organisationSlug: string) {
  return useApiMutation<OrganisationSessionSettingsResponse, UpdateOrganisationSessionSettingsRequest>({
    mutationFn: (body) => organisationsApi.updateSessionSettings(platformSlug, organisationSlug, body),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgSessionSettings(organisationSlug) })],
    successMessage: "Session settings saved",
  });
}

// ---------------------------------------------------------------------------
// Organisation user fields
// ---------------------------------------------------------------------------

export function useCreateUserField(platformSlug: string, organisationSlug: string) {
  return useApiMutation<OrganisationUserFieldResponse, CreateOrganisationUserFieldRequest>({
    mutationFn: (body) => organisationsApi.createUserField(platformSlug, organisationSlug, body),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgUserFields(organisationSlug) })],
    successMessage: "User field created",
  });
}

export function useUpdateUserField(platformSlug: string, organisationSlug: string) {
  return useApiMutation<OrganisationUserFieldResponse, { fieldId: number; body: UpdateOrganisationUserFieldRequest }>({
    mutationFn: ({ fieldId, body }) => organisationsApi.updateUserField(platformSlug, organisationSlug, fieldId, body),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgUserFields(organisationSlug) })],
    successMessage: "User field updated",
  });
}

export function useDeleteUserField(platformSlug: string, organisationSlug: string) {
  return useApiMutation<void, number>({
    mutationFn: (fieldId) => organisationsApi.deleteUserField(platformSlug, organisationSlug, fieldId),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgUserFields(organisationSlug) })],
    successMessage: "User field deleted",
  });
}

// ---------------------------------------------------------------------------
// Signing keys
// ---------------------------------------------------------------------------

export function useRotateOrgKey(platformSlug: string, organisationSlug: string) {
  return useApiMutation<OrganisationKeyResponse, void>({
    mutationFn: () => organisationsApi.rotateKey(platformSlug, organisationSlug),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgKeys(organisationSlug) })],
    successMessage: "Signing key rotated",
  });
}

// ---------------------------------------------------------------------------
// Organisation clients
// ---------------------------------------------------------------------------

export function useCreateOrgClient(platformSlug: string, organisationSlug: string) {
  return useApiMutation<OrganisationClientResponse, CreateOrganisationClientRequest>({
    mutationFn: (body) => organisationsApi.createClient(platformSlug, organisationSlug, body),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgClients(organisationSlug) })],
    successMessage: "Client created",
  });
}

export function useUpdateOrgClient(platformSlug: string, organisationSlug: string) {
  return useApiMutation<OrganisationClientResponse, { clientId: number; body: UpdateOrganisationClientRequest }>({
    mutationFn: ({ clientId, body }) =>
      organisationsApi.updateClient(platformSlug, organisationSlug, clientId, body),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgClients(organisationSlug) })],
    successMessage: "Client updated",
  });
}

export function useDeleteOrgClient(platformSlug: string, organisationSlug: string) {
  return useApiMutation<void, number>({
    mutationFn: (clientId) => organisationsApi.deleteClient(platformSlug, organisationSlug, clientId),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgClients(organisationSlug) })],
    successMessage: "Client deleted",
  });
}

export function useRotateOrgClientToken(platformSlug: string, organisationSlug: string) {
  return useApiMutation<OrganisationClientResponse, number>({
    mutationFn: (clientId) => organisationsApi.rotateClientToken(platformSlug, organisationSlug, clientId),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgClients(organisationSlug) })],
    successMessage: "Client token rotated",
  });
}
