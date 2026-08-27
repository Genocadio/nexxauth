"use client";

import { useQuery } from "@tanstack/react-query";
import { authApi } from "@/api/auth";
import { organisationsApi } from "@/api/organisations";
import { sessionsApi } from "@/api/sessions";
import { platformApi } from "@/api/platform";
import { queryKeys } from "@/lib/query-keys";
import { selectPlatformSession, useAppSelector } from "@/store/store";

/** Platform slug of the current platform session (undefined when logged out). */
export function usePlatformSlug(): string | undefined {
  const session = useAppSelector(selectPlatformSession);
  return session?.user.platform.slug;
}

// ---------------------------------------------------------------------------
// Platform level (slug comes from the session)
// ---------------------------------------------------------------------------

export function usePlatform() {
  const slug = usePlatformSlug();
  return useQuery({
    queryKey: queryKeys.platform,
    queryFn: () => platformApi.get(slug!),
    enabled: !!slug,
  });
}

export function usePlatformUsers() {
  const slug = usePlatformSlug();
  return useQuery({
    queryKey: queryKeys.platformUsers,
    queryFn: () => platformApi.users(slug!),
    enabled: !!slug,
  });
}

export function useOrganisations() {
  const slug = usePlatformSlug();
  return useQuery({
    queryKey: queryKeys.organisations,
    queryFn: () => organisationsApi.list(slug!),
    enabled: !!slug,
  });
}

export function useMyProfile() {
  const slug = usePlatformSlug();
  return useQuery({
    queryKey: queryKeys.me,
    queryFn: () => authApi.me(),
    enabled: !!slug,
  });
}

// ---------------------------------------------------------------------------
// Organisation level (organisationId comes from the route)
// ---------------------------------------------------------------------------

export function useOrganisation(organisationId: number) {
  const platformSlug = usePlatformSlug();
  return useQuery({
    queryKey: queryKeys.organisation(organisationId),
    queryFn: () => organisationsApi.get(platformSlug!, organisationId),
    enabled: !!platformSlug && !!organisationId,
  });
}

export function useOrgUsers(organisationId: number) {
  const platformSlug = usePlatformSlug();
  return useQuery({
    queryKey: queryKeys.orgUsers(organisationId),
    queryFn: () => organisationsApi.users(platformSlug!, organisationId),
    enabled: !!platformSlug && !!organisationId,
  });
}

export function useOrgRoles(organisationId: number) {
  const platformSlug = usePlatformSlug();
  return useQuery({
    queryKey: queryKeys.orgRoles(organisationId),
    queryFn: () => organisationsApi.roles(platformSlug!, organisationId),
    enabled: !!platformSlug && !!organisationId,
  });
}

export function useOrgAuthConfig(organisationId: number) {
  const platformSlug = usePlatformSlug();
  return useQuery({
    queryKey: queryKeys.orgAuthConfig(organisationId),
    queryFn: () => organisationsApi.authConfig(platformSlug!, organisationId),
    enabled: !!platformSlug && !!organisationId,
  });
}

export function useOrgSessionSettings(organisationId: number) {
  const platformSlug = usePlatformSlug();
  return useQuery({
    queryKey: queryKeys.orgSessionSettings(organisationId),
    queryFn: () => organisationsApi.sessionSettings(platformSlug!, organisationId),
    enabled: !!platformSlug && !!organisationId,
  });
}

export function useOrgUserFields(organisationId: number) {
  const platformSlug = usePlatformSlug();
  return useQuery({
    queryKey: queryKeys.orgUserFields(organisationId),
    queryFn: () => organisationsApi.userFields(platformSlug!, organisationId),
    enabled: !!platformSlug && !!organisationId,
  });
}

export function useOrgKeys(organisationId: number) {
  const platformSlug = usePlatformSlug();
  return useQuery({
    queryKey: queryKeys.orgKeys(organisationId),
    queryFn: () => organisationsApi.keys(platformSlug!, organisationId),
    enabled: !!platformSlug && !!organisationId,
  });
}

export function useOrgClients(organisationId: number) {
  const platformSlug = usePlatformSlug();
  return useQuery({
    queryKey: queryKeys.orgClients(organisationId),
    queryFn: () => organisationsApi.clients(platformSlug!, organisationId),
    enabled: !!platformSlug && !!organisationId,
  });
}

export function useOrgSessions(organisationId: number, userId?: number, clientKey?: string) {
  const platformSlug = usePlatformSlug();
  return useQuery({
    queryKey: [...queryKeys.orgSessions(organisationId, userId), clientKey ?? "all"],
    queryFn: () => sessionsApi.list(platformSlug!, organisationId, userId, clientKey),
    enabled: !!platformSlug && !!organisationId,
    refetchInterval: 30_000, // auto-refresh every 30s
  });
}
