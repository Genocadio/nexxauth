"use client";

import { useEffect } from "react";
import { Loader2 } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";
import { useOrganisations, usePlatformSlug } from "@/hooks/queries";

/**
 * Onboarding is dedicated to the organisation the user is currently working
 * in — it is never forced globally. Whenever the active organisation (the one
 * in the URL, e.g. /console/organisations/{slug}/...) has not finished its
 * setup wizard (`onboardingStep < 8`), the console redirects to that
 * organisation's wizard before anything else renders. Platform views
 * (organisation list, overview, profile, …) have no active organisation and
 * are never gated, and switching to an organisation that is already set up
 * never shows the wizard. The wizard itself is exempt so it doesn't redirect
 * to itself; completing the last step (8) clears the gate.
 *
 * Mounted inside the console layout (behind RequirePlatformAuth), so login,
 * refreshes and direct navigation all hit the same gate. A spinner is shown
 * while the organisation list loads so the console never flashes before the
 * redirect.
 */
export function OnboardingGate({ children }: { children: React.ReactNode }) {
  const platformSlug = usePlatformSlug();
  const organisations = useOrganisations();
  const pathname = usePathname();
  const router = useRouter();

  const onWizard = !!platformSlug && pathname.startsWith(`/console/onboarding/${platformSlug}`);

  // The "working" organisation is the one in the URL. Platform views
  // (/console/organisations, /console/overview, …) have no active org and are
  // never gated — only an org with unfinished onboarding forces the wizard.
  const segments = pathname.split("/");
  const activeOrgSlug = segments[2] === "organisations" ? segments[3] : undefined;
  const activeOrg = activeOrgSlug
    ? organisations.data?.find((o) => o.slug === activeOrgSlug)
    : undefined;
  const incomplete = !!activeOrg && (activeOrg.onboardingStep ?? 0) < 8;

  useEffect(() => {
    if (!platformSlug || !organisations.data || onWizard) return;
    if (!activeOrg || !incomplete) return;
    router.replace(`/console/onboarding/${platformSlug}?org=${activeOrg.slug}`);
  }, [platformSlug, organisations.data, onWizard, activeOrg, incomplete, router]);

  // The wizard manages its own loading; never gate it.
  if (onWizard) return <>{children}</>;

  // Platform views (no active organisation) are never gated.
  if (!activeOrgSlug) return <>{children}</>;

  // Wait for the organisation list before deciding, so the console never
  // flashes before the redirect. On an API error let the page render — the
  // pages have their own error state with a retry.
  if (organisations.isLoading || (!organisations.data && !organisations.isError)) {
    return (
      <div className="flex min-h-dvh items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  // A redirect is in flight.
  if (incomplete) {
    return (
      <div className="flex min-h-dvh items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return <>{children}</>;
}
