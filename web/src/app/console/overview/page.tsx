"use client";

import { useState } from "react";
import { Building2, CalendarDays, FileText, Globe, Plus, Server, Users } from "lucide-react";
import Link from "next/link";
import { FadeIn } from "@/components/shared/fade-in";
import { AnimatedCounter } from "@/components/shared/animated-counter";
import { TableSkeleton } from "@/components/shared/loading";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { CopyButton } from "@/components/shared/copy-button";
import { useOrganisations, usePlatform, usePlatformSlug, usePlatformUsers } from "@/hooks/queries";
import { resolvePlatformApiUrl } from "@/lib/api-url";
import { useBackendOrigin } from "@/lib/backend-url";
import { formatDate } from "@/lib/constants";

export default function OverviewPage() {
  const platform = usePlatform();
  const users = usePlatformUsers();
  const organisations = useOrganisations();
  const backendOrigin = useBackendOrigin();
  const platformSlug = usePlatformSlug() ?? "";

  const loading = platform.isLoading || users.isLoading || organisations.isLoading;

  const platformData = platform.data;
  const userCount = platformData?.userCount ?? users.data?.length ?? 0;
  const orgCount = organisations.data?.length ?? 0;
  const projectUrl = platformData
    ? resolvePlatformApiUrl(platformData.apiBaseUrl, platformData.slug, backendOrigin)
    : null;

  return (
    <div className="space-y-6">
      {/* Hero Section */}
      <FadeIn>
        <div className="relative overflow-hidden rounded-2xl border border-border/50 bg-gradient-to-br from-background via-background to-muted/30 p-6 sm:p-8 dark:from-background dark:via-background dark:to-zinc-900/50">
          <div className="absolute -right-20 -top-20 h-64 w-64 rounded-full bg-primary/5 blur-3xl" />
          <div className="absolute -bottom-16 -left-16 h-48 w-48 rounded-full bg-blue-500/5 blur-3xl" />

          <div className="relative">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">
                  {platformData?.name ?? "Platform"}
                </h1>
                <p className="mt-1 text-sm text-muted-foreground">
                  {platformData
                    ? `${platformData.slug} · Created ${formatDate(platformData.createdAt)}${projectUrl ? " · API ready" : ""}`
                    : "Your platform overview"}
                </p>
              </div>
              <Button asChild className="w-fit">
                <Link href="/console/organisations">
                  <Plus className="mr-1.5 h-4 w-4" />
                  New organisation
                </Link>
              </Button>
            </div>

            <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3">
              <HeroStat
                icon={<Building2 className="h-5 w-5" />}
                label="Organisations"
                value={orgCount}
                color="text-blue-600 dark:text-blue-400"
                bg="bg-blue-500/10"
              />
              <HeroStat
                icon={<Users className="h-5 w-5" />}
                label="Platform users"
                value={userCount}
                color="text-emerald-600 dark:text-emerald-400"
                bg="bg-emerald-500/10"
              />
              <Link href="/console/logs" className="group">
                <HeroStat
                  icon={<FileText className="h-5 w-5" />}
                  label="Audit logs"
                  value="View"
                  color="text-amber-600 dark:text-amber-400"
                  bg="bg-amber-500/10"
                  clickable
                />
              </Link>
            </div>
          </div>
        </div>
      </FadeIn>

      {/* Quick Connect + Organisations */}
      <div className="grid gap-6 lg:grid-cols-5">
        {/* Quick Connect — 2 cols */}
        <div className="lg:col-span-2">
          <FadeIn delay={0.1}>
            <QuickConnectCard platformSlug={platformSlug} projectUrl={projectUrl} />
          </FadeIn>
        </div>

        {/* Organisations — 3 cols */}
        <div className="lg:col-span-3">
          <FadeIn delay={0.15}>
            <Card className="h-full">
              <CardHeader className="pb-3">
                <div className="flex items-center justify-between">
                  <CardTitle className="text-base">Organisations</CardTitle>
                  {orgCount > 0 && (
                    <Button variant="ghost" size="sm" asChild>
                      <Link href="/console/organisations">
                        View all
                      </Link>
                    </Button>
                  )}
                </div>
              </CardHeader>
              <CardContent>
                {loading ? (
                  <TableSkeleton rows={3} columns={2} />
                ) : organisations.data && organisations.data.length > 0 ? (
                  <ul className="divide-y">
                    {organisations.data.slice(0, 5).map((org) => (
                      <li key={org.id}>
                        <Link
                          href={`/console/organisations/${org.slug}`}
                          className="group flex items-center justify-between gap-3 py-3 transition-colors hover:text-primary"
                        >
                          <div className="min-w-0">
                            <p className="truncate text-sm font-medium group-hover:text-primary transition-colors">{org.name}</p>
                            <p className="truncate text-xs text-muted-foreground">{org.slug}</p>
                          </div>
                          <div className="flex items-center gap-2">
                            <Badge variant="outline" className="text-[10px]">
                              {org.useEmailAsUsername ? "Email" : "Username"}
                            </Badge>
                            <span className="flex shrink-0 items-center gap-1.5 text-xs text-muted-foreground">
                              <CalendarDays className="h-3.5 w-3.5" />
                              {formatDate(org.createdAt)}
                            </span>
                          </div>
                        </Link>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <div className="flex flex-col items-center gap-3 py-8 text-center">
                    <Building2 className="h-8 w-8 text-muted-foreground/40" />
                    <p className="text-sm text-muted-foreground">No organisations yet</p>
                    <Button asChild size="sm">
                      <Link href="/console/organisations">Create your first org</Link>
                    </Button>
                  </div>
                )}
              </CardContent>
            </Card>
          </FadeIn>
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Quick Connect Card
// ---------------------------------------------------------------------------

function QuickConnectCard({
  platformSlug,
  projectUrl,
}: {
  platformSlug: string;
  projectUrl: string | null;
}) {
  const [snippet, setSnippet] = useState<"curl" | "fetch">("curl");

  const curlCmd = projectUrl
    ? `curl -X POST ${projectUrl}/auth/login \\
  -H "Content-Type: application/json" \\
  -d '{"email":"user@example.com","password":"..."}'`
    : "";

  const fetchCmd = projectUrl
    ? `const res = await fetch("${projectUrl}/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    email: "user@example.com",
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
        {projectUrl && (
          <div className="flex items-center justify-between rounded-lg bg-muted/50 px-3 py-2">
            <code className="truncate font-mono text-xs">{projectUrl}</code>
            <CopyButton value={projectUrl} label="Copy project URL" />
          </div>
        )}

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
          Login returns an access token (15m) and a refresh token (7d).
          Use the access token as a Bearer header for authenticated requests.
        </p>
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Hero Stat
// ---------------------------------------------------------------------------

function HeroStat({
  icon,
  label,
  value,
  color,
  bg,
  clickable,
}: {
  icon: React.ReactNode;
  label: string;
  value: number | string;
  color: string;
  bg: string;
  clickable?: boolean;
}) {
  return (
    <div
      className={`rounded-xl border border-border/40 bg-card/50 p-4 transition-all duration-200 ${
        clickable
          ? "group-hover:border-primary/30 group-hover:bg-primary/5 group-hover:shadow-sm"
          : "hover:bg-muted/30"
      }`}
    >
      <div className={`mb-3 flex h-9 w-9 items-center justify-center rounded-lg ${bg} ${color}`}>
        {icon}
      </div>
      <div className="text-2xl font-bold tracking-tight">
        {typeof value === "number" ? (
          <AnimatedCounter target={value} durationMs={1200} />
        ) : (
          value
        )}
      </div>
      <p className="mt-0.5 text-xs text-muted-foreground">{label}</p>
    </div>
  );
}
