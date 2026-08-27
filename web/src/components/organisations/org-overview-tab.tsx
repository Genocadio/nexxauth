"use client";

import { useCallback, useEffect, useState } from "react";
import {
  ArrowRight,
  Building2,
  Globe,
  KeyRound,
  Layers,
  Lightbulb,
  Pencil,
  RefreshCw,
  Server,
  Settings,
  ShieldCheck,
  Users,
  Wifi,
} from "lucide-react";
import Link from "next/link";
import { ErrorState } from "@/components/shared/error-state";
import { FormField } from "@/components/shared/form-field";
import { TableSkeleton } from "@/components/shared/loading";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { CopyButton } from "@/components/shared/copy-button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { StatCard } from "@/components/shared/stat-card";
import { sessionsApi } from "@/api/sessions";
import { useUpdateOrganisation } from "@/hooks/mutations";
import { useOrganisation, useOrgUsers, useOrgKeys, useOrgClients, usePlatform } from "@/hooks/queries";
import { useForm } from "@/hooks/use-form";
import { resolvePlatformApiUrl } from "@/lib/api-url";
import { useBackendOrigin } from "@/lib/backend-url";
import { formatDate } from "@/lib/constants";
import { updateOrganisationSchema } from "@/lib/validation";
import type { OrganisationSessionResponse } from "@/types/api";

interface OrgOverviewTabProps {
  platformSlug: string;
  organisationId: number;
}

export function OrgOverviewTab({ platformSlug, organisationId }: OrgOverviewTabProps) {
  const org = useOrganisation(organisationId);
  const platform = usePlatform();
  const users = useOrgUsers(organisationId);
  const keys = useOrgKeys(organisationId);
  const clients = useOrgClients(organisationId);
  const backendOrigin = useBackendOrigin();

  const [editOpen, setEditOpen] = useState(false);
  const update = useUpdateOrganisation(platformSlug, organisationId);

  const projectUrl = resolvePlatformApiUrl(platform.data?.apiBaseUrl, platformSlug, backendOrigin);

  const form = useForm(updateOrganisationSchema, {
    name: org.data?.name ?? "",
    description: org.data?.description ?? "",
  });

  // Session stats
  const [sessionStats, setSessionStats] = useState<{ active: number; total: number }>({ active: 0, total: 0 });
  const [loadingSessions, setLoadingSessions] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoadingSessions(true);
    sessionsApi
      .list(platformSlug, organisationId)
      .then((data) => {
        if (!cancelled) {
          setSessionStats({
            active: data.filter((s) => s.active).length,
            total: data.length,
          });
        }
      })
      .catch(() => {})
      .finally(() => {
        if (!cancelled) setLoadingSessions(false);
      });
    return () => { cancelled = true; };
  }, [platformSlug, organisationId]);

  if (org.isLoading) return <TableSkeleton rows={4} columns={2} />;
  if (org.isError) return <ErrorState error={org.error} onRetry={() => org.refetch()} />;

  const data = org.data!;
  const userCount = users.data?.length ?? 0;
  const keyCount = keys.data?.length ?? 0;
  const clientCount = clients.data?.length ?? 0;

  return (
    <div className="space-y-6">
      {/* Stat cards */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          icon={Users}
          label="Users"
          value={userCount}
          sub="in this org"
          gradient="bg-gradient-to-br from-blue-500/10 to-indigo-500/10 text-blue-600 dark:text-blue-400"
        />
        <StatCard
          icon={Wifi}
          label="Active sessions"
          value={loadingSessions ? "…" : sessionStats.active}
          sub={`${sessionStats.total} total`}
          gradient="bg-gradient-to-br from-emerald-500/10 to-teal-500/10 text-emerald-600 dark:text-emerald-400"
        />
        <StatCard
          icon={KeyRound}
          label="Signing keys"
          value={keyCount}
          sub={keyCount > 0 ? "active" : "none yet"}
          gradient="bg-gradient-to-br from-violet-500/10 to-purple-500/10 text-violet-600 dark:text-violet-400"
        />
        <StatCard
          icon={ShieldCheck}
          label="API clients"
          value={clientCount}
          sub={clientCount > 0 ? "registered" : "none yet"}
          gradient="bg-gradient-to-br from-amber-500/10 to-orange-500/10 text-amber-600 dark:text-amber-400"
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-5">
        {/* Quick Connect — 2 cols */}
        <div className="lg:col-span-2">
          <OrgQuickConnect platformSlug={platformSlug} orgId={organisationId} projectUrl={projectUrl} />
        </div>

        {/* Details + Quick Links — 3 cols */}
        <div className="lg:col-span-3 space-y-6">
          {/* Details card */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center justify-between text-base">
                <span className="flex items-center gap-2">
                  <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-gradient-to-br from-primary/10 to-primary/5 text-primary">
                    <Building2 className="h-3.5 w-3.5" />
                  </div>
                  Organisation details
                </span>
                <Button variant="outline" size="sm" className="gap-2" onClick={() => setEditOpen(true)}>
                  <Pencil /> Edit
                </Button>
              </CardTitle>
              <CardDescription>Identity and sign-in behaviour of this organisation.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              <DetailRow label="Name" value={data.name} />
              <DetailRow label="Slug" value={data.slug} mono />
              <DetailRow label="Description" value={data.description ?? "—"} />
              <DetailRow
                label="Login identifier"
                value={data.useEmailAsUsername ? "Email" : "Username"}
              />
              <DetailRow label="Created" value={formatDate(data.createdAt)} />
            </CardContent>
          </Card>

          {/* Quick links */}
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">Quick links</CardTitle>
            </CardHeader>
            <CardContent className="space-y-1">
              <OrgQuickLink href={`/console/organisations/${data.slug}?tab=users`} icon={<Users className="h-4 w-4" />} label="Manage users" />
              <OrgQuickLink href={`/console/organisations/${data.slug}?tab=roles`} icon={<ShieldCheck className="h-4 w-4" />} label="Roles & permissions" />
              <OrgQuickLink href={`/console/organisations/${data.slug}?tab=sessions`} icon={<Wifi className="h-4 w-4" />} label="Active sessions" />
              <OrgQuickLink href={`/console/organisations/${data.slug}?tab=keys`} icon={<KeyRound className="h-4 w-4" />} label="Signing keys" />
              <OrgQuickLink href={`/console/organisations/${data.slug}?tab=settings`} icon={<Settings className="h-4 w-4" />} label="Session settings" />
              <OrgQuickLink href={`/docs/${platformSlug}/${organisationId}`} icon={<Globe className="h-4 w-4" />} label="API documentation" />
            </CardContent>
          </Card>
        </div>
      </div>

      {/* Tips */}
      <OrgTips
        userCount={userCount}
        keyCount={keyCount}
        clientCount={clientCount}
        sessionCount={sessionStats.active}
        orgSlug={data.slug}
        platformSlug={platformSlug}
        organisationId={organisationId}
      />

      {/* Edit dialog */}
      <Dialog open={editOpen} onOpenChange={(next) => !update.isPending && setEditOpen(next)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Edit organisation</DialogTitle>
            <DialogDescription>The slug can never be changed.</DialogDescription>
          </DialogHeader>
          <form
            id="org-edit-form"
            onSubmit={form.handleSubmit(async (values) => {
              await update.mutateAsync({
                name: values.name.trim() || undefined,
                description: values.description?.trim() || undefined,
              });
              setEditOpen(false);
            })}
            className="space-y-4"
          >
            <FormField label="Name" htmlFor="oe-name" error={form.errors.name}>
              <Input
                id="oe-name"
                value={form.values.name}
                onChange={(e) => form.setValue("name", e.target.value)}
              />
            </FormField>
            <FormField label="Description" htmlFor="oe-description" error={form.errors.description}>
              <Textarea
                id="oe-description"
                rows={3}
                value={form.values.description}
                onChange={(e) => form.setValue("description", e.target.value)}
              />
            </FormField>
            {form.submitError ? (
              <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
                {form.submitError}
              </p>
            ) : null}
          </form>
          <DialogFooter>
            <Button type="submit" form="org-edit-form" disabled={update.isPending}>
              Save changes
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Quick Connect for org
// ---------------------------------------------------------------------------

function OrgQuickConnect({
  platformSlug,
  orgId,
  projectUrl,
}: {
  platformSlug: string;
  orgId: number;
  projectUrl: string | null;
}) {
  const [snippet, setSnippet] = useState<"curl" | "fetch">("curl");

  const baseUrl = projectUrl ? `${projectUrl}` : "";

  const curlCmd = baseUrl
    ? `curl -X POST ${baseUrl}/auth/login \\
  -H "Content-Type: application/json" \\
  -d '{
    "organisationId": ${orgId},
    "identifier": "user@example.com",
    "identifierType": "EMAIL",
    "password": "..."
  }'`
    : "";

  const fetchCmd = baseUrl
    ? `const res = await fetch("${baseUrl}/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    organisationId: ${orgId},
    identifier: "user@example.com",
    identifierType: "EMAIL",
    password: "..."
  })
});
const { accessToken, refreshToken } = await res.json();`
    : "";

  return (
    <Card className="h-full">
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-base">
          <Server className="h-4 w-4" />
          Quick connect
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {baseUrl && (
          <div className="flex items-center justify-between rounded-lg bg-muted/50 px-3 py-2">
            <code className="truncate font-mono text-xs">{baseUrl}</code>
            <CopyButton value={baseUrl} label="Copy project URL" />
          </div>
        )}

        <div className="flex items-center gap-2">
          <Badge variant="outline" className="text-[10px]">
            Org ID: {orgId}
          </Badge>
          <Badge variant="outline" className="text-[10px]">
            {baseUrl ? "HTTPS" : "—"}
          </Badge>
        </div>

        <div className="flex gap-1">
          <Button
            variant={snippet === "curl" ? "default" : "ghost"}
            size="sm"
            className="h-7 text-xs"
            onClick={() => setSnippet("curl")}
          >
            cURL
          </Button>
          <Button
            variant={snippet === "fetch" ? "default" : "ghost"}
            size="sm"
            className="h-7 text-xs"
            onClick={() => setSnippet("fetch")}
          >
            fetch()
          </Button>
        </div>

        <div className="relative rounded-lg bg-muted/50 p-3">
          <pre className="overflow-x-auto text-[11px] leading-relaxed text-muted-foreground">
            <code>{snippet === "curl" ? curlCmd : fetchCmd}</code>
          </pre>
          <CopyButton
            value={snippet === "curl" ? curlCmd : fetchCmd}
            label="Copy snippet"
            className="absolute right-2 top-2"
          />
        </div>

        <p className="text-[11px] text-muted-foreground/60">
          Org auth returns signed JWTs. Tokens are scoped to this organisation only.
        </p>
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Quick Link
// ---------------------------------------------------------------------------

function OrgQuickLink({
  href,
  icon,
  label,
}: {
  href: string;
  icon: React.ReactNode;
  label: string;
}) {
  return (
    <Link
      href={href}
      className="group flex items-center gap-3 rounded-lg px-3 py-2 transition-colors hover:bg-muted/50"
    >
      <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-muted text-muted-foreground transition-colors group-hover:bg-primary/10 group-hover:text-primary">
        {icon}
      </div>
      <span className="flex-1 text-sm font-medium">{label}</span>
      <ArrowRight className="h-3.5 w-3.5 text-muted-foreground/40 transition-transform group-hover:translate-x-0.5 group-hover:text-primary" />
    </Link>
  );
}

// ---------------------------------------------------------------------------
// Tips
// ---------------------------------------------------------------------------

function OrgTips({
  userCount,
  keyCount,
  clientCount,
  sessionCount,
  orgSlug,
  platformSlug,
  organisationId,
}: {
  userCount: number;
  keyCount: number;
  clientCount: number;
  sessionCount: number;
  orgSlug: string;
  platformSlug: string;
  organisationId: number;
}) {
  const tips: { text: string; href?: string }[] = [];

  if (userCount === 0) {
    tips.push({
      text: "Add users to this organisation so they can authenticate.",
      href: `/console/organisations/${orgSlug}?tab=users`,
    });
  }
  if (userCount > 0 && sessionCount === 0) {
    tips.push({
      text: "No active sessions yet. Users will appear here once they log in.",
    });
  }
  if (keyCount <= 1) {
    tips.push({
      text: "Rotate signing keys regularly. Add a second key before rotating to avoid downtime.",
      href: `/console/organisations/${orgSlug}?tab=keys`,
    });
  }
  if (clientCount === 0) {
    tips.push({
      text: "Create an API client to get a static token for server-to-server integrations.",
      href: `/console/organisations/${orgSlug}?tab=clients`,
    });
  }
  tips.push({
    text: "Use the session settings to control token lifetimes and concurrent session limits.",
    href: `/console/organisations/${orgSlug}?tab=settings`,
  });

  return (
    <Card className="border-dashed">
      <CardContent className="flex items-start gap-3 p-4">
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-amber-500/10 text-amber-600 dark:text-amber-400">
          <Lightbulb className="h-4 w-4" />
        </div>
        <div className="space-y-1.5">
          <p className="text-sm font-medium">Tips</p>
          <ul className="space-y-1">
            {tips.map((tip, i) => (
              <li key={i} className="text-xs text-muted-foreground">
                {tip.href ? (
                  <Link href={tip.href} className="text-primary hover:underline">
                    {tip.text}
                  </Link>
                ) : (
                  tip.text
                )}
              </li>
            ))}
          </ul>
        </div>
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Detail Row
// ---------------------------------------------------------------------------

function DetailRow({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex items-center justify-between gap-4 rounded-lg px-2 py-1.5 transition-colors hover:bg-muted/50">
      <span className="text-muted-foreground">{label}</span>
      <span className={`max-w-[65%] truncate text-right font-medium ${mono ? "font-mono text-xs" : ""}`}>
        {value}
      </span>
    </div>
  );
}
