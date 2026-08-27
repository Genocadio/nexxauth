"use client";

import {
  useMutation,
  useQueryClient,
  type QueryClient,
  type UseMutationResult,
} from "@tanstack/react-query";
import { toast } from "sonner";
import { organisationsApi } from "@/api/organisations";
import { sessionsApi } from "@/api/sessions";
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

export function useUpdateOrganisation(platformSlug: string, organisationId: number) {
  return useApiMutation<OrganisationResponse, UpdateOrganisationRequest>({
    mutationFn: (body) => organisationsApi.update(platformSlug, organisationId, body),
    invalidate: [
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.organisations }),
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.organisation(organisationId) }),
    ],
    successMessage: "Organisation updated",
  });
}

export function useDeleteOrganisation(platformSlug: string, organisationId: number) {
  return useApiMutation<void, void>({
    mutationFn: () => organisationsApi.remove(platformSlug, organisationId),
    invalidate: [
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.organisations }),
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.platform }),
      (qc) => qc.removeQueries({ queryKey: queryKeys.organisation(organisationId) }),
    ],
    successMessage: "Organisation deleted",
  });
}

// ---------------------------------------------------------------------------
// Organisation users
// ---------------------------------------------------------------------------

export function useCreateOrgUser(platformSlug: string, organisationId: number) {
  return useApiMutation<OrganisationUserResponse, CreateOrganisationUserRequest>({
    mutationFn: (body) => organisationsApi.createUser(platformSlug, organisationId, body),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgUsers(organisationId) })],
    successMessage: "User created",
  });
}

export function useUpdateOrgUser(platformSlug: string, organisationId: number) {
  return useApiMutation<OrganisationUserResponse, { userId: number; body: UpdateOrganisationUserRequest }>({
    mutationFn: ({ userId, body }) => organisationsApi.updateUser(platformSlug, organisationId, userId, body),
    invalidate: [
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.orgUsers(organisationId) }),
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.orgRoles(organisationId) }),
    ],
    successMessage: "User updated",
  });
}

export function useDeleteOrgUser(platformSlug: string, organisationId: number) {
  return useApiMutation<void, number>({
    mutationFn: (userId) => organisationsApi.deleteUser(platformSlug, organisationId, userId),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgUsers(organisationId) })],
    successMessage: "User deleted",
  });
}

// ---------------------------------------------------------------------------
// Organisation roles
// ---------------------------------------------------------------------------

export function useCreateOrgRole(platformSlug: string, organisationId: number) {
  return useApiMutation<OrganisationRoleResponse, CreateOrganisationRoleRequest>({
    mutationFn: (body) => organisationsApi.createRole(platformSlug, organisationId, body),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgRoles(organisationId) })],
    successMessage: "Role created",
  });
}

export function useUpdateOrgRole(platformSlug: string, organisationId: number) {
  return useApiMutation<OrganisationRoleResponse, { roleId: number; body: UpdateOrganisationRoleRequest }>({
    mutationFn: ({ roleId, body }) => organisationsApi.updateRole(platformSlug, organisationId, roleId, body),
    invalidate: [
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.orgRoles(organisationId) }),
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.orgUsers(organisationId) }),
    ],
    successMessage: "Role updated",
  });
}

export function useDeleteOrgRole(platformSlug: string, organisationId: number) {
  return useApiMutation<void, number>({
    mutationFn: (roleId) => organisationsApi.deleteRole(platformSlug, organisationId, roleId),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgRoles(organisationId) })],
    successMessage: "Role deleted",
  });
}

// ---------------------------------------------------------------------------
// Org auth config & session settings
// ---------------------------------------------------------------------------

export function useUpdateOrgAuthConfig(platformSlug: string, organisationId: number) {
  return useApiMutation<OrganisationAuthConfigResponse, UpdateOrganisationAuthConfigRequest>({
    mutationFn: (body) => organisationsApi.updateAuthConfig(platformSlug, organisationId, body),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgAuthConfig(organisationId) })],
    successMessage: "Authentication settings saved",
  });
}

export function useUpdateOrgSessionSettings(platformSlug: string, organisationId: number) {
  return useApiMutation<OrganisationSessionSettingsResponse, UpdateOrganisationSessionSettingsRequest>({
    mutationFn: (body) => organisationsApi.updateSessionSettings(platformSlug, organisationId, body),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgSessionSettings(organisationId) })],
    successMessage: "Session settings saved",
  });
}

// ---------------------------------------------------------------------------
// Organisation user fields
// ---------------------------------------------------------------------------

export function useCreateUserField(platformSlug: string, organisationId: number) {
  return useApiMutation<OrganisationUserFieldResponse, CreateOrganisationUserFieldRequest>({
    mutationFn: (body) => organisationsApi.createUserField(platformSlug, organisationId, body),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgUserFields(organisationId) })],
    successMessage: "User field created",
  });
}

export function useUpdateUserField(platformSlug: string, organisationId: number) {
  return useApiMutation<OrganisationUserFieldResponse, { fieldId: number; body: UpdateOrganisationUserFieldRequest }>({
    mutationFn: ({ fieldId, body }) => organisationsApi.updateUserField(platformSlug, organisationId, fieldId, body),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgUserFields(organisationId) })],
    successMessage: "User field updated",
  });
}

export function useDeleteUserField(platformSlug: string, organisationId: number) {
  return useApiMutation<void, number>({
    mutationFn: (fieldId) => organisationsApi.deleteUserField(platformSlug, organisationId, fieldId),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgUserFields(organisationId) })],
    successMessage: "User field deleted",
  });
}

// ---------------------------------------------------------------------------
// Signing keys
// ---------------------------------------------------------------------------

export function useRotateOrgKey(platformSlug: string, organisationId: number) {
  return useApiMutation<OrganisationKeyResponse, void>({
    mutationFn: () => organisationsApi.rotateKey(platformSlug, organisationId),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgKeys(organisationId) })],
    successMessage: "Signing key rotated",
  });
}

// ---------------------------------------------------------------------------
// Organisation clients
// ---------------------------------------------------------------------------

export function useCreateOrgClient(platformSlug: string, organisationId: number) {
  return useApiMutation<OrganisationClientResponse, CreateOrganisationClientRequest>({
    mutationFn: (body) => organisationsApi.createClient(platformSlug, organisationId, body),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgClients(organisationId) })],
    successMessage: "Client created",
  });
}

export function useUpdateOrgClient(platformSlug: string, organisationId: number) {
  return useApiMutation<OrganisationClientResponse, { clientKey: string; body: UpdateOrganisationClientRequest }>({
    mutationFn: ({ clientKey, body }) =>
      organisationsApi.updateClient(platformSlug, organisationId, clientKey, body),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgClients(organisationId) })],
    successMessage: "Client updated",
  });
}

export function useDeleteOrgClient(platformSlug: string, organisationId: number) {
  return useApiMutation<void, string>({
    mutationFn: (clientKey) => organisationsApi.deleteClient(platformSlug, organisationId, clientKey),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgClients(organisationId) })],
    successMessage: "Client deleted",
  });
}

export function useRotateOrgClientToken(platformSlug: string, organisationId: number) {
  return useApiMutation<OrganisationClientResponse, string>({
    mutationFn: (clientKey) => organisationsApi.rotateClientToken(platformSlug, organisationId, clientKey),
    invalidate: [(qc) => qc.invalidateQueries({ queryKey: queryKeys.orgClients(organisationId) })],
    successMessage: "Client token rotated",
  });
}

// ---------------------------------------------------------------------------
// Sessions
// ---------------------------------------------------------------------------

export function useRevokeSession(platformSlug: string, organisationId: number) {
  return useApiMutation<void, string>({
    mutationFn: (sessionId) => sessionsApi.revoke(platformSlug, organisationId, sessionId),
    invalidate: [
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.orgSessions(organisationId) }),
    ],
    successMessage: "Session revoked",
  });
}

export function useRevokeAllUserSessions(platformSlug: string, organisationId: number) {
  return useApiMutation<void, number>({
    mutationFn: (userId) => sessionsApi.revokeAllForUser(platformSlug, organisationId, userId),
    invalidate: [
      (qc) => qc.invalidateQueries({ queryKey: queryKeys.orgSessions(organisationId) }),
    ],
    successMessage: "All user sessions revoked",
  });
}
