"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Activity,
  AlertTriangle,
  CircleDot,
  Filter,
  Info,
  ShieldAlert,
  XCircle,
} from "lucide-react";
import { FadeIn } from "@/components/shared/fade-in";
import { PageHeader } from "@/components/shared/page-header";
import { CardsSkeleton, TableSkeleton } from "@/components/shared/loading";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { logsApi } from "@/api/logs";
import { useLogStream } from "@/hooks/use-log-stream";
import { useOrganisations, usePlatformSlug } from "@/hooks/queries";
import { getErrorMessage } from "@/types/errors";
import type { LogEntryResponse, LogPage } from "@/types/api";

const LEVEL_OPTIONS = ["INFO", "WARN", "ERROR"] as const;

const CATEGORY_OPTIONS = [
  { value: "AUTH", label: "Auth", description: "Login, register, refresh, logout, password" },
  { value: "USER_MANAGEMENT", label: "Users", description: "Create, update, delete users" },
  { value: "ORG_MANAGEMENT", label: "Orgs", description: "Create, update, delete organisations" },
  { value: "CONFIG", label: "Config", description: "Roles, fields, keys, clients" },
  { value: "SECURITY", label: "Security", description: "Failures, token reuse, disabled accounts" },
] as const;

const CATEGORY_STYLES: Record<string, string> = {
  AUTH: "bg-blue-500/10 text-blue-600 dark:text-blue-400",
  USER_MANAGEMENT: "bg-violet-500/10 text-violet-600 dark:text-violet-400",
  ORG_MANAGEMENT: "bg-indigo-500/10 text-indigo-600 dark:text-indigo-400",
  CONFIG: "bg-teal-500/10 text-teal-600 dark:text-teal-400",
  SECURITY: "bg-red-500/10 text-red-600 dark:text-red-400",
};

const EVENT_TYPES = [
  "PLATFORM_REGISTER",
  "PLATFORM_LOGIN_SUCCESS",
  "PLATFORM_LOGIN_FAILURE",
  "PLATFORM_REFRESH",
  "PLATFORM_LOGOUT",
  "PLATFORM_PASSWORD_CHANGED",
  "PLATFORM_USER_ADDED",
  "PLATFORM_TOKEN_REUSE",
  "PLATFORM_DISABLED",
  "ORG_REGISTER",
  "ORG_LOGIN_SUCCESS",
  "ORG_LOGIN_FAILURE",
  "ORG_REFRESH",
  "ORG_LOGOUT",
  "ORG_PASSWORD_CHANGED",
  "ORG_CREATED",
  "ORG_UPDATED",
  "ORG_DELETED",
  "ORG_KEY_ROTATED",
  "ORG_TOKEN_REUSE",
  "ORG_USER_CREATED",
  "ORG_USER_UPDATED",
  "ORG_USER_DELETED",
  "ORG_ROLE_CREATED",
  "ORG_ROLE_UPDATED",
  "ORG_ROLE_DELETED",
  "ORG_CLIENT_CREATED",
  "ORG_CLIENT_UPDATED",
  "ORG_CLIENT_DELETED",
  "ORG_CLIENT_TOKEN_ROTATED",
  "ORG_USER_FIELD_CREATED",
  "ORG_USER_FIELD_UPDATED",
  "ORG_USER_FIELD_DELETED",
] as const;

const LEVEL_STYLES: Record<string, string> = {
  INFO: "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400",
  WARN: "bg-amber-500/10 text-amber-600 dark:text-amber-400",
  ERROR: "bg-red-500/10 text-red-600 dark:text-red-400",
};

const LEVEL_ICONS: Record<string, typeof Info> = {
  INFO: Info,
  WARN: AlertTriangle,
  ERROR: XCircle,
};

export default function LogsPage() {
  const platformSlug = usePlatformSlug() ?? "";
  const organisations = useOrganisations();

  // Filters
  const [level, setLevel] = useState<string>("all");
  const [category, setCategory] = useState<string>("all");
  const [riskMode, setRiskMode] = useState(false);
  const [eventType, setEventType] = useState<string>("all");
  const [clientFilter, setClientFilter] = useState<string>("all");
  const [domainFilter, setDomainFilter] = useState<string>("all");
  const [orgId, setOrgId] = useState<string>("all");
  const [from, setFrom] = useState<string>("");
  const [to, setTo] = useState<string>("");
  const [page, setPage] = useState(0);
  const [size] = useState(50);

  // Historical data
  const [historyData, setHistoryData] = useState<LogPage | null>(null);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyError, setHistoryError] = useState<string | null>(null);

  // Real-time
  const orgIdNum = orgId !== "all" ? Number(orgId) : undefined;
  const { logs: realtimeLogs, connected, clearLogs } = useLogStream(orgIdNum);

  // Build query params — datetime-local values are converted to ISO-8601
  const queryParams = useMemo(
    () => ({
      level: level !== "all" ? level : undefined,
      category: riskMode ? "SECURITY" : category !== "all" ? category : undefined,
      eventType: eventType !== "all" ? eventType : undefined,
      clientKey: clientFilter === "__none__" ? "__none__" : clientFilter !== "all" ? clientFilter : undefined,
      domain: domainFilter === "__none__" ? "__none__" : domainFilter !== "all" ? domainFilter : undefined,
      organisationId: orgId !== "all" ? Number(orgId) : undefined,
      from: from ? new Date(from).toISOString() : undefined,
      to: to ? new Date(to).toISOString() : undefined,
      page,
      size,
    }),
    [level, category, riskMode, eventType, clientFilter, domainFilter, orgId, from, to, page, size],
  );

  // Fetch historical logs
  const fetchLogs = useCallback(async () => {
    if (!platformSlug) return;
    setHistoryLoading(true);
    setHistoryError(null);
    try {
      const data = await logsApi.list(platformSlug, queryParams);
      setHistoryData(data);
    } catch (err) {
      setHistoryError(getErrorMessage(err));
    } finally {
      setHistoryLoading(false);
    }
  }, [platformSlug, queryParams]);

  useEffect(() => {
    fetchLogs();
  }, [fetchLogs]);

  // Reset page when filters change
  useEffect(() => {
    setPage(0);
  }, [level, category, eventType, clientFilter, domainFilter, orgId, from, to]);

  // Merge: realtime logs at the top, then historical page below
  const allLogs = useMemo(() => {
    const seen = new Set(realtimeLogs.map((l) => l.id));
    const historical = (historyData?.content ?? []).filter((l) => !seen.has(l.id));
    return [...realtimeLogs, ...historical];
  }, [realtimeLogs, historyData]);

  const totalPages = historyData?.totalPages ?? 0;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Logs"
        description="Real-time and historical log entries across your platform and organisations."
      />

      {/* Connection status */}
      <FadeIn delay={0.05}>
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <span
            className={`h-2 w-2 rounded-full ${connected ? "bg-emerald-500" : "bg-muted-foreground/40"}`}
          />
          {connected ? "Live streaming" : "Connecting…"}
        </div>
      </FadeIn>

      {/* Filters */}
      <FadeIn delay={0.1}>
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="flex items-center gap-2 text-sm font-medium">
              <Filter className="h-4 w-4" />
              Filters
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <div className="space-y-1">
                <label className="text-xs font-medium text-muted-foreground">Level</label>
                <Select value={level} onValueChange={setLevel}>
                  <SelectTrigger>
                    <SelectValue placeholder="All levels" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All levels</SelectItem>
                    {LEVEL_OPTIONS.map((l) => (
                      <SelectItem key={l} value={l}>
                        {l}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-medium text-muted-foreground">Category</label>
                <Select value={category} onValueChange={setCategory}>
                  <SelectTrigger>
                    <SelectValue placeholder="All categories" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All categories</SelectItem>
                    {CATEGORY_OPTIONS.map((c) => (
                      <SelectItem key={c.value} value={c.value}>
                        {c.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-medium text-muted-foreground">Event type</label>
                <Select value={eventType} onValueChange={setEventType}>
                  <SelectTrigger>
                    <SelectValue placeholder="All events" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All events</SelectItem>
                    {EVENT_TYPES.map((e) => (
                      <SelectItem key={e} value={e}>
                        {e}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-medium text-muted-foreground">Organisation</label>
                <Select value={orgId} onValueChange={setOrgId}>
                  <SelectTrigger>
                    <SelectValue placeholder="All organisations" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All organisations</SelectItem>
                    {(organisations.data ?? []).map((org) => (
                      <SelectItem key={org.id} value={String(org.id)}>
                        {org.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-medium text-muted-foreground">Client</label>
                <Select value={clientFilter} onValueChange={setClientFilter}>
                  <SelectTrigger>
                    <SelectValue placeholder="All clients" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All clients</SelectItem>
                    {[...new Set((historyData?.content ?? []).map((l) => l.clientKey).filter(Boolean))].map((ck) => (
                      <SelectItem key={ck!} value={ck!}>{ck}</SelectItem>
                    ))}
                    <SelectItem value="__none__">Unknown / direct</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-medium text-muted-foreground">Domain</label>
                <Select value={domainFilter} onValueChange={setDomainFilter}>
                  <SelectTrigger>
                    <SelectValue placeholder="All domains" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All domains</SelectItem>
                    {[...new Set((historyData?.content ?? []).map((l) => l.domain).filter(Boolean))].map((d) => (
                      <SelectItem key={d!} value={d!}>{d}</SelectItem>
                    ))}
                    <SelectItem value="__none__">No domain</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-medium text-muted-foreground">From</label>
                <Input
                  type="datetime-local"
                  value={from}
                  onChange={(e) => setFrom(e.target.value)}
                  className="text-xs"
                />
              </div>

              <div className="space-y-1">
                <label className="text-xs font-medium text-muted-foreground">To</label>
                <Input
                  type="datetime-local"
                  value={to}
                  onChange={(e) => setTo(e.target.value)}
                  className="text-xs"
                />
              </div>

              <div className="flex items-end gap-2">
                <Button
                  variant={riskMode ? "default" : "outline"}
                  size="sm"
                  onClick={() => {
                    setRiskMode((v) => !v);
                    setPage(0);
                  }}
                  className={riskMode ? "bg-amber-500/20 text-amber-700 hover:bg-amber-500/30 dark:text-amber-400" : ""}
                >
                  <ShieldAlert className="mr-1 h-3.5 w-3.5" />
                  Risk
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    setLevel("all");
                    setCategory("all");
                    setRiskMode(false);
                    setEventType("all");
                    setOrgId("all");
                    setFrom("");
                    setTo("");
                    setClientFilter("all");
                    setDomainFilter("all");
                    clearLogs();
                    setPage(0);
                  }}
                  className="w-full"
                >
                  <XCircle className="mr-1 h-3.5 w-3.5" />
                  Clear filters
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      </FadeIn>

      {/* Log entries */}
      <FadeIn delay={0.15}>
        <Card>
          <CardHeader className="pb-3">
            <div className="flex items-center justify-between">
              <CardTitle className="text-sm font-medium">Entries</CardTitle>
              <span className="text-xs text-muted-foreground">
                {historyData?.totalElements ?? allLogs.length} total
              </span>
            </div>
          </CardHeader>
          <CardContent className="p-0">
            {historyLoading && !allLogs.length ? (
              <div className="p-4">
                <TableSkeleton rows={8} columns={5} />
              </div>
            ) : historyError ? (
              <div className="flex flex-col items-center gap-2 py-12 text-center">
                <AlertTriangle className="h-8 w-8 text-destructive/60" />
                <p className="text-sm text-destructive">{historyError}</p>
                <Button variant="outline" size="sm" onClick={fetchLogs}>
                  Try again
                </Button>
              </div>
            ) : allLogs.length === 0 ? (
              <div className="flex flex-col items-center gap-3 py-16 text-center">
                <Activity className="h-8 w-8 text-muted-foreground/40" />
                <p className="text-sm text-muted-foreground">No log entries yet.</p>
              </div>
            ) : (
              <div className="divide-y">
                {allLogs.map((entry) => (
                  <LogRow key={entry.id} entry={entry} />
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </FadeIn>

      {/* Pagination */}
      {totalPages > 1 && (
        <FadeIn delay={0.2}>
          <div className="flex items-center justify-between">
            <p className="text-xs text-muted-foreground">
              Page {page + 1} of {totalPages}
            </p>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              >
                Next
              </Button>
            </div>
          </div>
        </FadeIn>
      )}
    </div>
  );
}

const LEVEL_BORDER: Record<string, string> = {
  INFO: "border-l-emerald-500",
  WARN: "border-l-amber-500",
  ERROR: "border-l-red-500",
};

const LEVEL_BG: Record<string, string> = {
  INFO: "hover:bg-emerald-500/5",
  WARN: "hover:bg-amber-500/5",
  ERROR: "hover:bg-red-500/5",
};

function LogRow({ entry }: { entry: LogEntryResponse }) {
  const [expanded, setExpanded] = useState(false);
  const Icon = LEVEL_ICONS[entry.level] ?? CircleDot;
  const levelStyle = LEVEL_STYLES[entry.level] ?? "bg-muted text-muted-foreground";
  const borderStyle = LEVEL_BORDER[entry.level] ?? "border-l-muted";
  const bgStyle = LEVEL_BG[entry.level] ?? "";

  return (
    <div
      className={`border-l-2 ${borderStyle} transition-colors ${bgStyle} ${expanded ? "bg-muted/40" : ""}`}
    >
      {/* Main row — clickable to expand */}
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        className="flex w-full items-start gap-3 px-4 py-3 text-left"
      >
        {/* Level icon */}
        <div className={`mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-md ${levelStyle}`}>
          <Icon className="h-3.5 w-3.5" />
        </div>

        {/* Content */}
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="outline" className={`text-[10px] ${levelStyle}`}>
              {entry.level}
            </Badge>
            {entry.category && (
              <Badge variant="outline" className={`text-[10px] ${CATEGORY_STYLES[entry.category] ?? "bg-muted text-muted-foreground"}`}>
                {entry.category.replace("_", " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())}
              </Badge>
            )}
            <span className="font-mono text-xs font-medium text-foreground">{entry.eventType}</span>
            {entry.organisationSlug && (
              <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">
                {entry.organisationSlug}
              </span>
            )}
          </div>
          {entry.message && entry.message !== entry.eventType && (
            <p className="mt-0.5 text-xs text-muted-foreground">{entry.message}</p>
          )}
          {!expanded && (
            <div className="mt-1 flex flex-wrap gap-x-3 gap-y-0.5 text-[11px] text-muted-foreground/70">
              {entry.actor && <span>Actor: {entry.actor}</span>}
              {entry.ip && <span>IP: {entry.ip}</span>}
              {entry.domain && <span>Domain: {entry.domain}</span>}
              {entry.detail && <span>Detail: {entry.detail}</span>}
              {entry.requestId && (
                <span className="font-mono">Req: {entry.requestId.slice(0, 8)}</span>
              )}
            </div>
          )}
        </div>

        {/* Timestamp + expand chevron */}
        <div className="flex shrink-0 flex-col items-end gap-1">
          <time className="text-[11px] text-muted-foreground/60">
            {new Date(entry.createdAt).toLocaleTimeString()}
          </time>
          <ChevronIcon expanded={expanded} />
        </div>
      </button>

      {/* Expanded detail panel */}
      {expanded && (
        <div className="border-t border-dashed border-border/60 bg-muted/20 px-4 py-3">
          <dl className="grid gap-x-6 gap-y-2 text-[11px] sm:grid-cols-2 lg:grid-cols-3">
            <DetailRow label="Event type" value={entry.eventType} mono />
            <DetailRow label="Category" value={entry.category ?? "—"} />
            <DetailRow label="Level" value={entry.level} />
            <DetailRow label="Timestamp" value={new Date(entry.createdAt).toLocaleString()} />
            <DetailRow label="Organisation" value={entry.organisationSlug ?? "— (platform)"} />
            <DetailRow label="Organisation ID" value={entry.organisationId != null ? String(entry.organisationId) : "—"} />
            <DetailRow label="Actor" value={entry.actor ?? "—"} />
            <DetailRow label="IP address" value={entry.ip ?? "—"} mono />
            <DetailRow label="Client" value={entry.clientKey ?? "direct"} mono />
            <DetailRow label="Domain" value={entry.domain ?? "—"} mono />
            <DetailRow label="Request ID" value={entry.requestId ?? "—"} mono />
            <DetailRow label="Message" value={entry.message ?? "—"} />
            <DetailRow label="Detail" value={entry.detail ?? "—"} />
            <DetailRow label="Log entry ID" value={String(entry.id)} mono />
            <DetailRow label="Created" value={entry.createdAt} mono />
          </dl>
        </div>
      )}
    </div>
  );
}

function DetailRow({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="font-medium text-muted-foreground/60 uppercase tracking-wider">{label}</dt>
      <dd className={`truncate ${mono ? "font-mono" : ""} text-foreground/80`}>{value}</dd>
    </div>
  );
}

function ChevronIcon({ expanded }: { expanded: boolean }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={`h-3.5 w-3.5 text-muted-foreground/40 transition-transform duration-200 ${expanded ? "rotate-90" : ""}`}
    >
      <path d="m9 18 6-6-6-6" />
    </svg>
  );
}
