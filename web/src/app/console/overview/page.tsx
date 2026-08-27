"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ArrowRight,
  BookOpen,
  Building2,
  CalendarDays,
  FileText,
  Globe,
  Lightbulb,
  Plus,
  RefreshCw,
  Server,
  ShieldCheck,
  Terminal,
  Users,
} from "lucide-react";
import Link from "next/link";
import { FadeIn } from "@/components/shared/fade-in";
import { AnimatedCounter } from "@/components/shared/animated-counter";
import { CardsSkeleton, TableSkeleton } from "@/components/shared/loading";
import { StatCard } from "@/components/shared/stat-card";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CopyButton } from "@/components/shared/copy-button";
import { Badge } from "@/components/ui/badge";
import { logsApi } from "@/api/logs";
import { useOrganisations, usePlatform, usePlatformSlug, usePlatformUsers } from "@/hooks/queries";
import { resolvePlatformApiUrl } from "@/lib/api-url";
import { useBackendOrigin } from "@/lib/backend-url";
import { formatDate } from "@/lib/constants";
import { ROLE_META } from "@/types/enums";
import type { LogEntryResponse } from "@/types/api";

export default function OverviewPage() {
  const platform = usePlatform();
  const users = usePlatformUsers();
  const organisations = useOrganisations();
  const backendOrigin = useBackendOrigin();
  const platformSlug = usePlatformSlug() ?? "";

  const loading = platform.isLoading || users.isLoading || organisations.isLoading;
  const error = platform.error ?? users.error ?? organisations.error;

  const platformData = platform.data;
  const userCount = platformData?.userCount ?? users.data?.length ?? 0;
  const orgCount = organisations.data?.length ?? 0;
  const projectUrl = platformData
    ? resolvePlatformApiUrl(platformData.apiBaseUrl, platformData.slug, backendOrigin)
    : null;

  return (
    <div className="space-y-8">
      {/* Hero Section */}
      <FadeIn>
        <div className="relative overflow-hidden rounded-2xl border border-border/50 bg-gradient-to-br from-background via-background to-muted/30 p-6 sm:p-8 dark:from-background dark:via-background dark:to-zinc-900/50">
          {/* Background decoration */}
          <div className="absolute -right-20 -top-20 h-64 w-64 rounded-full bg-primary/5 blur-3xl" />
          <div className="absolute -bottom-16 -left-16 h-48 w-48 rounded-full bg-blue-500/5 blur-3xl" />
          <div className="absolute right-1/4 top-1/2 h-32 w-32 -translate-y-1/2 rounded-full bg-violet-500/5 blur-2xl dark:bg-violet-500/10" />

          <div className="relative">
            {/* Title row */}
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">
                  {platformData?.name ?? "Platform"}
                </h1>
                <p className="mt-1 text-sm text-muted-foreground">
                  {platformData
                    ? `${platformData.slug} · Created ${formatDate(platformData.createdAt)}
                    · API ${projectUrl ? "ready" : "not configured"}`
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

            {/* Animated stats */}
            <div className="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-4">
              <HeroStat
                icon={<Building2 className="h-5 w-5" />}
                label="Organisations"
                value={orgCount}
                color="text-blue-600 dark:text-blue-400"
                bg="bg-blue-500/10"
              />
              <HeroStat
                icon={<Users className="h-5 w-5" />}
                label="Team members"
                value={userCount}
                color="text-emerald-600 dark:text-emerald-400"
                bg="bg-emerald-500/10"
              />
              <HeroStat
                icon={<ShieldCheck className="h-5 w-5" />}
                label="Your role"
                value="Admin"
                color="text-violet-600 dark:text-violet-400"
                bg="bg-violet-500/10"
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

      {/* Quick Connect + Recent Activity */}
      {loading && (
        <CardsSkeleton />
      )}
      {error && !loading && (
        <p className="text-sm text-destructive">Couldn&apos;t load the overview.</p>
      )}

      <div className="grid gap-6 lg:grid-cols-5">
        {/* Quick Connect — 2 cols */}
        <div className="lg:col-span-2">
          <FadeIn delay={0.1}>
            <QuickConnectCard
              platformSlug={platformSlug}
              projectUrl={projectUrl}
            />
          </FadeIn>
        </div>

        {/* Recent Logs — 3 cols */}
        <div className="lg:col-span-3">
          <FadeIn delay={0.15}>
            <RecentLogsCard platformSlug={platformSlug} />
          </FadeIn>
        </div>
      </div>

      {/* Quick Actions + Org List */}
      <div className="grid gap-6 lg:grid-cols-3">
        {/* Quick Actions */}
        <FadeIn delay={0.2}>
          <Card className="h-full">
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-base">
                <Terminal className="h-4 w-4" />
                Quick actions
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              <QuickAction
                href="/console/organisations"
                icon={<Building2 className="h-4 w-4" />}
                label="Create organisation"
                description="Set up a new tenant"
              />
              <QuickAction
                href="/console/users"
                icon={<Users className="h-4 w-4" />}
                label="Manage users"
                description="Add team members"
              />
              <QuickAction
                href="/console/logs"
                icon={<FileText className="h-4 w-4" />}
                label="View audit logs"
                description="Security & activity trail"
              />
              <QuickAction
                href="/docs"
                icon={<BookOpen className="h-4 w-4" />}
                label="API documentation"
                description="Integration guides"
              />
            </CardContent>
          </Card>
        </FadeIn>

        {/* Recent Organisations */}
        <FadeIn delay={0.25}>
          <Card className="lg:col-span-2 h-full">
            <CardHeader className="pb-3">
              <div className="flex items-center justify-between">
                <CardTitle className="text-base">Recent organisations</CardTitle>
                {orgCount > 0 && (
                  <Button variant="ghost" size="sm" asChild>
                    <Link href="/console/organisations">
                      View all
                      <ArrowRight className="ml-1 h-3.5 w-3.5" />
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

      {/* Tips */}
      <FadeIn delay={0.3}>
        <TipsCard orgCount={orgCount} userCount={userCount} />
      </FadeIn>
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
// Recent Logs Card
// ---------------------------------------------------------------------------

function RecentLogsCard({ platformSlug }: { platformSlug: string }) {
  const [logs, setLogs] = useState<LogEntryResponse[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const loading = !loaded;

  useEffect(() => {
    if (!platformSlug) return;
    let cancelled = false;
    logsApi
      .list(platformSlug, { page: 0, size: 5 })
      .then((data) => {
        if (!cancelled) setLogs(data.content);
      })
      .catch(() => {
        // silently fail — logs are optional
      })
      .finally(() => {
        if (!cancelled) setLoaded(true);
      });
    return () => {
      cancelled = true;
    };
  }, [platformSlug, refreshKey]);

  const levelColors: Record<string, string> = {
    INFO: "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400",
    WARN: "bg-amber-500/10 text-amber-600 dark:text-amber-400",
    ERROR: "bg-red-500/10 text-red-600 dark:text-red-400",
  };

  return (
    <Card className="h-full">
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle className="flex items-center gap-2 text-base">
            <FileText className="h-4 w-4" />
            Recent activity
          </CardTitle>
          <div className="flex items-center gap-1">
            <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => { setLoaded(false); setRefreshKey((k) => k + 1); }}>
              <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} />
            </Button>
            <Button variant="ghost" size="sm" className="h-7 text-xs" asChild>
              <Link href="/console/logs">
                View all
                <ArrowRight className="ml-1 h-3 w-3" />
              </Link>
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        {loading && logs.length === 0 ? (
          <TableSkeleton rows={5} columns={3} />
        ) : logs.length === 0 ? (
          <div className="flex flex-col items-center gap-2 py-8 text-center">
            <FileText className="h-6 w-6 text-muted-foreground/40" />
            <p className="text-xs text-muted-foreground">No log entries yet</p>
          </div>
        ) : (
          <div className="space-y-1">
            {logs.map((log) => (
              <div
                key={log.id}
                className="flex items-center gap-2 rounded-md px-2 py-1.5 text-xs transition-colors hover:bg-muted/50"
              >
                <span className={`inline-flex h-5 items-center rounded px-1.5 text-[10px] font-medium ${levelColors[log.level] ?? "bg-muted"}`}>
                  {log.level}
                </span>
                <span className="flex-1 truncate font-mono text-muted-foreground/70">
                  {log.eventType}
                </span>
                {log.organisationSlug && (
                  <span className="hidden sm:inline rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">
                    {log.organisationSlug}
                  </span>
                )}
                <span className="shrink-0 text-[10px] text-muted-foreground/50">
                  {new Date(log.createdAt).toLocaleTimeString()}
                </span>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Quick Action
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

function QuickAction({
  href,
  icon,
  label,
  description,
}: {
  href: string;
  icon: React.ReactNode;
  label: string;
  description: string;
}) {
  return (
    <Link
      href={href}
      className="group flex items-center gap-3 rounded-lg px-3 py-2.5 transition-colors hover:bg-muted/50"
    >
      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-muted text-muted-foreground transition-colors group-hover:bg-primary/10 group-hover:text-primary">
        {icon}
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium">{label}</p>
        <p className="text-xs text-muted-foreground">{description}</p>
      </div>
      <ArrowRight className="h-3.5 w-3.5 text-muted-foreground/40 transition-transform group-hover:translate-x-0.5 group-hover:text-primary" />
    </Link>
  );
}

// ---------------------------------------------------------------------------
// Tips Card
// ---------------------------------------------------------------------------

function TipsCard({ orgCount, userCount }: { orgCount: number; userCount: number }) {
  const tips: { text: string; href?: string }[] = [];

  if (orgCount === 0) {
    tips.push({
      text: "Create your first organisation to start authenticating users.",
      href: "/console/organisations",
    });
  }
  if (orgCount > 0 && userCount <= 1) {
    tips.push({
      text: "Add team members so others can manage your organisations.",
      href: "/console/users",
    });
  }
  tips.push({
    text: "Each organisation gets its own signing keys, session settings, and API clients.",
  });
  tips.push({
    text: "Use the docs for integration guides — cURL, JavaScript, Python examples.",
    href: "/docs",
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
