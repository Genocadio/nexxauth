"use client";

import { useEffect } from "react";
import { Loader2 } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";
import { useOrganisations, usePlatformSlug } from "@/hooks/queries";

/**
 * Forces the platform user through onboarding before they can use the console:
 * as soon as any organisation has not completed its setup wizard
 * (`onboardingStep < 8`), every console page redirects to the wizard for that
 * organisation. The wizard itself is exempt so it doesn't redirect to itself;
 * completing the last step (8) clears the gate.
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
  const incomplete = organisations.data?.find((o) => (o.onboardingStep ?? 0) < 8);

  useEffect(() => {
    if (!platformSlug || !organisations.data || onWizard) return;
    if (!incomplete) return;
    router.replace(`/console/onboarding/${platformSlug}?org=${incomplete.slug}`);
  }, [platformSlug, organisations.data, onWizard, incomplete, router]);

  // The wizard manages its own loading; never gate it.
  if (onWizard) return <>{children}</>;

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
