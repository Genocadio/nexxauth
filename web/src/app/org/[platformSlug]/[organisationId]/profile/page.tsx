import { ShieldCheck } from "lucide-react";
import { redirect } from "next/navigation";
import { cookies } from "next/headers";
import { AuthShell } from "@/components/auth/auth-shell";
import { OrgLogoutButton } from "@/components/org/org-logout-button";
import { CopyButton } from "@/components/shared/copy-button";
import { InitialsAvatar } from "@/components/shared/initials-avatar";
import { UserRoles } from "@/components/shared/status-badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { organisationApiUrl, platformApiUrl } from "@/lib/api-url";
import { formatDate } from "@/lib/constants";
import { ORG_ACCESS_COOKIE, orgSlugFromToken } from "@/lib/org-auth";
import { fetchOrgSession } from "@/lib/org-session";
import { fullName, type OrganisationUserResponse } from "@/types/api";

/**
 * Server-rendered org user profile. The session is verified server-side via
 * the session route handler (which also rotates/clears cookies); the profile
 * data comes straight from the backend — nothing is fetched on the client.
 * The Next proxy redirects unauthenticated requests here back to the login.
 */
export const dynamic = "force-dynamic";

export default async function OrgProfilePage({
  params,
}: {
  params: Promise<{ platformSlug: string; organisationId: string }>;
}) {
  const { platformSlug, organisationId: organisationIdParam } = await params;
  const organisationId = Number(organisationIdParam);

  const session = await fetchOrgSession(platformSlug);
  if (!session.authenticated || !session.user) {
    redirect(`/org/${platformSlug}/${organisationId}`);
  }

  const cookieStore = await cookies();
  const organisationSlug =
    orgSlugFromToken(cookieStore.get(ORG_ACCESS_COOKIE)?.value ?? "") ?? `#${organisationId}`;

  const platformBase = platformApiUrl(process.env.BACKEND_PUBLIC_URL ?? "", platformSlug);
  const apiUrl =
    platformBase && !organisationSlug.startsWith("#")
      ? organisationApiUrl(platformBase, organisationSlug)
      : null;

  return (
    <OrgProfile
      user={session.user}
      organisationSlug={organisationSlug}
      platformSlug={platformSlug}
      organisationId={organisationId}
      apiUrl={apiUrl}
    />
  );
}

function OrgProfile({
  user,
  organisationSlug,
  platformSlug,
  organisationId,
  apiUrl,
}: {
  user: OrganisationUserResponse;
  organisationSlug: string;
  platformSlug: string;
  organisationId: number;
  apiUrl: string | null;
}) {
  const name = fullName(user);

  return (
    <AuthShell
      title="Organisation portal"
      subtitle={`Signed in to ${organisationSlug}`}
      footer={<OrgLogoutButton platformSlug={platformSlug} organisationId={organisationId} />}
    >
      <div className="space-y-4">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-3 text-base">
              <InitialsAvatar name={name} className="h-10 w-10" />
              {name}
            </CardTitle>
            <CardDescription>{user?.email ?? user?.username ?? "Organisation user"}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            {apiUrl ? <UrlRow label="API URL" value={apiUrl} /> : null}
            <Row label="Username" value={user?.username ?? "—"} />
            <Row label="Email" value={user?.email ?? "—"} />
            <Row label="Status" value={user?.enabled ? "Active" : "Disabled"} />
            <Row label="Joined" value={user ? formatDate(user.createdAt) : "—"} />
            <div className="flex items-center justify-between gap-4">
              <span className="text-muted-foreground">Roles</span>
              <span className="text-right">
                {user ? <UserRoles roles={user.roles} /> : "—"}
              </span>
            </div>
            {user && user.metadata && Object.keys(user.metadata).length > 0 ? (
              <div className="rounded-lg border p-3">
                <p className="mb-2 flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
                  <ShieldCheck className="h-3.5 w-3.5" /> Your fields
                </p>
                <dl className="space-y-1.5">
                  {Object.entries(user.metadata).map(([key, value]) => (
                    <div key={key} className="flex justify-between gap-4 text-sm">
                      <dt className="text-muted-foreground">{key}</dt>
                      <dd className="truncate font-medium">{value || "—"}</dd>
                    </div>
                  ))}
                </dl>
              </div>
            ) : null}
          </CardContent>
        </Card>
      </div>
    </AuthShell>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <span className="text-muted-foreground">{label}</span>
      <span className="max-w-[65%] truncate text-right font-medium">{value}</span>
    </div>
  );
}

function UrlRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <span className="text-muted-foreground">{label}</span>
      <div className="flex min-w-0 items-center justify-end gap-1.5">
        <code className="max-w-[70%] truncate font-mono text-xs font-medium">{value}</code>
        <CopyButton value={value} label={`Copy ${label}`} className="shrink-0" />
      </div>
    </div>
  );
}
