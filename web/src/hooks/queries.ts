"use client";

import { useQuery } from "@tanstack/react-query";
import { authApi } from "@/api/auth";
import { organisationsApi } from "@/api/organisations";
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
// Organisation level (slug comes from the route)
// ---------------------------------------------------------------------------

export function useOrganisation(organisationSlug: string) {
  const platformSlug = usePlatformSlug();
  return useQuery({
    queryKey: queryKeys.organisation(organisationSlug),
    queryFn: () => organisationsApi.get(platformSlug!, organisationSlug),
    enabled: !!platformSlug && !!organisationSlug,
  });
}

export function useOrgUsers(organisationSlug: string) {
  const platformSlug = usePlatformSlug();
  return useQuery({
    queryKey: queryKeys.orgUsers(organisationSlug),
    queryFn: () => organisationsApi.users(platformSlug!, organisationSlug),
    enabled: !!platformSlug && !!organisationSlug,
  });
}

export function useOrgRoles(organisationSlug: string) {
  const platformSlug = usePlatformSlug();
  return useQuery({
    queryKey: queryKeys.orgRoles(organisationSlug),
    queryFn: () => organisationsApi.roles(platformSlug!, organisationSlug),
    enabled: !!platformSlug && !!organisationSlug,
  });
}

export function useOrgAuthConfig(organisationSlug: string) {
  const platformSlug = usePlatformSlug();
  return useQuery({
    queryKey: queryKeys.orgAuthConfig(organisationSlug),
    queryFn: () => organisationsApi.authConfig(platformSlug!, organisationSlug),
    enabled: !!platformSlug && !!organisationSlug,
  });
}

export function useOrgSessionSettings(organisationSlug: string) {
  const platformSlug = usePlatformSlug();
  return useQuery({
    queryKey: queryKeys.orgSessionSettings(organisationSlug),
    queryFn: () => organisationsApi.sessionSettings(platformSlug!, organisationSlug),
    enabled: !!platformSlug && !!organisationSlug,
  });
}

export function useOrgUserFields(organisationSlug: string) {
  const platformSlug = usePlatformSlug();
  return useQuery({
    queryKey: queryKeys.orgUserFields(organisationSlug),
    queryFn: () => organisationsApi.userFields(platformSlug!, organisationSlug),
    enabled: !!platformSlug && !!organisationSlug,
  });
}

export function useOrgKeys(organisationSlug: string) {
  const platformSlug = usePlatformSlug();
  return useQuery({
    queryKey: queryKeys.orgKeys(organisationSlug),
    queryFn: () => organisationsApi.keys(platformSlug!, organisationSlug),
    enabled: !!platformSlug && !!organisationSlug,
  });
}

export function useOrgClients(organisationSlug: string) {
  const platformSlug = usePlatformSlug();
  return useQuery({
    queryKey: queryKeys.orgClients(organisationSlug),
    queryFn: () => organisationsApi.clients(platformSlug!, organisationSlug),
    enabled: !!platformSlug && !!organisationSlug,
  });
}
