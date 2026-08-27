import { Globe, Monitor, ShieldCheck, Smartphone, Wifi } from "lucide-react";
import { redirect } from "next/navigation";
import { cookies } from "next/headers";
import { AuthShell } from "@/components/auth/auth-shell";
import { OrgLogoutButton } from "@/components/org/org-logout-button";
import { CopyButton } from "@/components/shared/copy-button";
import { InitialsAvatar } from "@/components/shared/initials-avatar";
import { UserRoles } from "@/components/shared/status-badge";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { platformApiUrl } from "@/lib/api-url";
import { formatDate } from "@/lib/constants";
import { ORG_ACCESS_COOKIE, orgSlugFromToken, serverOrgSessions, type OrgSessionInfo } from "@/lib/org-auth";
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
  const accessToken = cookieStore.get(ORG_ACCESS_COOKIE)?.value ?? "";
  const organisationSlug =
    orgSlugFromToken(accessToken) ?? `#${organisationId}`;

  // The project base URL — `BACKEND_PUBLIC_URL` + slug. Never a /api/v1 path.
  const projectUrl = platformApiUrl(process.env.BACKEND_PUBLIC_URL ?? "", platformSlug);

  // Fetch this user's sessions for the "My sessions" section.
  let sessions: OrgSessionInfo[] = [];
  const sessionsResult = await serverOrgSessions(platformSlug, organisationId, session.user.id, accessToken);
  if (sessionsResult.ok) {
    sessions = sessionsResult.data;
  }

  return (
    <OrgProfile
      user={session.user}
      organisationSlug={organisationSlug}
      platformSlug={platformSlug}
      organisationId={organisationId}
      projectUrl={projectUrl}
      sessions={sessions}
    />
  );
}

function OrgProfile({
  user,
  organisationSlug,
  platformSlug,
  organisationId,
  projectUrl,
  sessions,
}: {
  user: OrganisationUserResponse;
  organisationSlug: string;
  platformSlug: string;
  organisationId: number;
  projectUrl: string | null;
  sessions: OrgSessionInfo[];
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
            {projectUrl ? <UrlRow label="Project URL" value={projectUrl} /> : null}
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

      {/* My sessions */}
      {sessions.length > 0 && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="flex items-center gap-2 text-base">
              <Wifi className="h-4 w-4" />
              My sessions
            </CardTitle>
            <CardDescription>
              Active and recent sessions for your account. Revoke any session you don&apos;t recognise.
            </CardDescription>
          </CardHeader>
          <CardContent className="p-0">
            <div className="divide-y">
              {sessions.map((s) => (
                <OrgSessionRow key={s.sessionId} session={s} />
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </AuthShell>
  );
}

function OrgSessionRow({ session }: { session: OrgSessionInfo }) {
  const isExpired = new Date(session.expiresAt) < new Date();
  const ua = session.userAgent ?? "";
  const isMobile = /Mobile|Android|iPhone|iPad/i.test(ua);
  let browser = "Unknown";
  if (/Chrome/i.test(ua) && !/Edg/i.test(ua)) browser = "Chrome";
  else if (/Safari/i.test(ua) && !/Chrome/i.test(ua)) browser = "Safari";
  else if (/Firefox/i.test(ua)) browser = "Firefox";
  else if (/Edg/i.test(ua)) browser = "Edge";

  let os = "Unknown";
  if (/Mac OS X/i.test(ua)) os = "macOS";
  else if (/Windows/i.test(ua)) os = "Windows";
  else if (/iPhone|iPad/i.test(ua)) os = "iOS";
  else if (/Android/i.test(ua)) os = "Android";
  else if (/Linux/i.test(ua)) os = "Linux";

  return (
    <div className="flex items-center gap-3 px-4 py-3">
      <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${
        session.active
          ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
          : "bg-muted text-muted-foreground"
      }`}>
        {isMobile ? <Smartphone className="h-4 w-4" /> : <Monitor className="h-4 w-4" />}
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium">{browser} on {os}</span>
          <Badge variant="outline" className={`text-[10px] ${
            session.active
              ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
              : isExpired
                ? "bg-red-500/10 text-red-600 dark:text-red-400"
                : "bg-muted text-muted-foreground"
          }`}>
            {session.active ? "Active" : isExpired ? "Expired" : "Revoked"}
          </Badge>
        </div>
        <div className="mt-0.5 flex flex-wrap gap-x-3 text-[11px] text-muted-foreground/70">
          {session.ipAddress && (
            <span className="flex items-center gap-1">
              <Globe className="h-3 w-3" />
              {session.ipAddress}
            </span>
          )}
          <span>Started: {new Date(session.createdAt).toLocaleDateString()}</span>
          <span>Expires: {new Date(session.expiresAt).toLocaleDateString()}</span>
        </div>
      </div>
    </div>
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
