import { redirect } from "next/navigation";
import { AuthShell } from "@/components/auth/auth-shell";
import { OrgLoginForm } from "@/components/org/org-login-form";
import { fetchOrgSession } from "@/lib/org-session";

/**
 * Server-rendered org portal. An unauthenticated visitor sees the login form;
 * a session cookie (verified against the backend by the session route
 * handler) redirects straight to the server-rendered profile.
 */
export const dynamic = "force-dynamic";

export default async function OrgPortalPage({
  params,
}: {
  params: Promise<{ platformSlug: string; organisationId: string }>;
}) {
  const { platformSlug, organisationId: organisationIdParam } = await params;
  const organisationId = Number(organisationIdParam);

  const session = await fetchOrgSession(platformSlug);
  if (session.authenticated) {
    redirect(`/org/${platformSlug}/${organisationId}/profile`);
  }

  return (
    <AuthShell
      title="Organisation sign in"
      subtitle={`Sign in to organisation #${organisationId} under ${platformSlug}.`}
      footer="Your organisation was invited here by your administrator."
    >
      <OrgLoginForm platformSlug={platformSlug} organisationId={organisationId} />
    </AuthShell>
  );
}
