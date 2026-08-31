"use client";

import { useCallback, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  ChevronRight,
  Clock,
  Globe,
  Monitor,
  RefreshCw,
  Shield,
  ShieldOff,
  Smartphone,
  User,
  Wifi,
} from "lucide-react";
import { ErrorState } from "@/components/shared/error-state";
import { TableSkeleton } from "@/components/shared/loading";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { sessionsApi } from "@/api/sessions";
import {
  useOrgSessions,
  useOrgUsers,
  usePlatformSlug,
} from "@/hooks/queries";
import { queryKeys } from "@/lib/query-keys";
import type { SessionTimelineEvent } from "@/types/api";
import { useRevokeSession, useRevokeAllUserSessions } from "@/hooks/mutations";

interface OrgSessionsTabProps {
  platformSlug: string;
  organisationId: number;
}

export function OrgSessionsTab({ platformSlug, organisationId }: OrgSessionsTabProps) {
  const [filterUserId, setFilterUserId] = useState<string>("all");
  const [filterClient, setFilterClient] = useState<string>("all");
  const userId = filterUserId !== "all" ? Number(filterUserId) : undefined;
  const clientKey = filterClient === "__none__" ? "__none__" : filterClient === "all" ? undefined : filterClient;

  const sessions = useOrgSessions(organisationId, userId, clientKey);
  const users = useOrgUsers(organisationId);
  const revokeSession = useRevokeSession(platformSlug, organisationId);
  const revokeAll = useRevokeAllUserSessions(platformSlug, organisationId);

  if (sessions.isLoading) return <TableSkeleton rows={4} columns={4} />;
  if (sessions.isError)
    return <ErrorState error={sessions.error} onRetry={() => sessions.refetch()} />;

  const data = sessions.data ?? [];

  // Compute stats
  const activeSessions = data.filter((s) => s.active).length;
  const uniqueUsers = new Set(data.map((s) => s.userId)).size;

  return (
    <div className="space-y-6">
      {/* Stats */}
      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard
          icon={<Wifi className="h-4 w-4" />}
          label="Active sessions"
          value={activeSessions}
          color="emerald"
        />
        <StatCard
          icon={<Monitor className="h-4 w-4" />}
          label="Total sessions"
          value={data.length}
          color="blue"
        />
        <StatCard
          icon={<User className="h-4 w-4" />}
          label="Users with sessions"
          value={uniqueUsers}
          color="violet"
        />
      </div>

      {/* Filters */}
      <Card>
        <CardHeader className="pb-3">
          <div className="flex items-center justify-between">
            <div>
              <CardTitle className="flex items-center gap-2 text-sm font-medium">
                <Shield className="h-4 w-4" />
                Sessions
              </CardTitle>
              <CardDescription>
                Active and recent sessions across all users in this organisation.
              </CardDescription>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Select value={filterUserId} onValueChange={setFilterUserId}>
                <SelectTrigger className="w-[160px]">
                  <SelectValue placeholder="All users" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All users</SelectItem>
                  {(users.data ?? []).map((u) => (
                    <SelectItem key={u.id} value={String(u.id)}>
                      {u.username ?? u.email ?? u.firstName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Select value={filterClient} onValueChange={setFilterClient}>
                <SelectTrigger className="w-[160px]">
                  <SelectValue placeholder="All clients" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All clients</SelectItem>
                  {/* Build unique client list from sessions, showing clientName */}
                  {[...new Map(
                    (sessions.data ?? [])
                      .filter((s) => s.clientKey)
                      .map((s) => [s.clientKey, s.clientName ?? s.clientKey])
                  ).entries()].map(([key, name]) => (
                    <SelectItem key={key!} value={key!}>{name}</SelectItem>
                  ))}
                  <SelectItem value="__none__">Unknown client</SelectItem>
                </SelectContent>
              </Select>
              {filterUserId !== "all" && (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    const uid = Number(filterUserId);
                    revokeAll.mutate(uid);
                    setFilterUserId("all");
                  }}
                  disabled={revokeAll.isPending}
                >
                  <ShieldOff className="mr-1 h-3.5 w-3.5" />
                  Revoke all
                </Button>
              )}
            </div>
          </div>
        </CardHeader>
        <CardContent className="p-0">
          {data.length === 0 ? (
            <div className="flex flex-col items-center gap-3 py-16 text-center">
              <Shield className="h-8 w-8 text-muted-foreground/40" />
              <p className="text-sm text-muted-foreground">No sessions found.</p>
            </div>
          ) : (
            <div className="divide-y">
              {data.map((session) => (
                <SessionRow
                  key={session.sessionId}
                  session={session}
                  platformSlug={platformSlug}
                  organisationId={organisationId}
                  onRevoke={() => revokeSession.mutate(session.sessionId)}
                  isRevoking={revokeSession.isPending}
                />
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Session row
// ---------------------------------------------------------------------------

function SessionRow({
  session,
  platformSlug,
  organisationId,
  onRevoke,
  isRevoking,
}: {
  session: {
    sessionId: string;
    userId: number;
    userIdentifier: string;
    ipAddress: string | null;
    userAgent: string | null;
    clientKey: string | null;
    clientName: string | null;
    clientType: string | null;
    hostname: string | null;
    createdAt: string;
    lastActivityAt: string;
    expiresAt: string;
    active: boolean;
    tokenCount: number;
  };
  platformSlug: string;
  organisationId: number;
  onRevoke: () => void;
  isRevoking: boolean;
}) {
  const [expanded, setExpanded] = useState(false);
  const device = parseDevice(session.userAgent);
  const isExpired = new Date(session.expiresAt) < new Date();
  const deviceLabel = [device.browser, device.browserVersion, device.os, device.osVersion]
    .filter(Boolean)
    .join(" ");
  const deviceDetail = [device.device, device.type !== "desktop" ? device.type : null]
    .filter(Boolean)
    .join(" ");

  return (
    <div>
      {/* Main row — clickable to expand */}
      <div
        role="button"
        tabIndex={0}
        onClick={() => setExpanded((v) => !v)}
        onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); setExpanded((v) => !v); } }}
        className={`flex w-full items-center gap-4 px-4 py-3 text-left transition-colors hover:bg-muted/30 ${expanded ? "bg-muted/20" : ""} ${!session.active ? "opacity-60" : ""}`}
      >
        {/* Expand chevron */}
        <ChevronRight
          className={`h-4 w-4 shrink-0 text-muted-foreground/40 transition-transform duration-200 ${expanded ? "rotate-90" : ""}`}
        />

        {/* Device icon */}
        <div
          className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${
            session.active
              ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
              : "bg-muted text-muted-foreground"
          }`}
        >
          {device.type === "mobile" || device.type === "tablet" ? (
            <Smartphone className="h-4 w-4" />
          ) : device.type === "bot" ? (
            <Globe className="h-4 w-4" />
          ) : (
            <Monitor className="h-4 w-4" />
          )}
        </div>

        {/* Info */}
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-sm font-medium">{session.userIdentifier}</span>
            <Badge
              variant="outline"
              className={`text-[10px] ${
                session.active
                  ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
                  : isExpired
                    ? "bg-red-500/10 text-red-600 dark:text-red-400"
                    : "bg-muted text-muted-foreground"
              }`}
            >
              {session.active ? "Active" : isExpired ? "Expired" : "Revoked"}
            </Badge>
            {session.tokenCount > 1 && (
              <span className="text-[10px] text-muted-foreground">
                {session.tokenCount} rotations
              </span>
            )}
            {session.clientName || session.clientKey ? (
              <Badge variant="outline" className="bg-violet-500/10 text-violet-600 dark:text-violet-400 text-[10px]">
                {session.clientName ?? session.clientKey}
                {session.clientType ? (
                  <span className="ml-1 uppercase opacity-60">{session.clientType}</span>
                ) : null}
              </Badge>
            ) : (
              <Badge variant="outline" className="text-[10px] text-muted-foreground/60">
                direct
              </Badge>
            )}
          </div>
          <div className="mt-0.5 flex flex-wrap gap-x-3 gap-y-0.5 text-[11px] text-muted-foreground/70">
            {session.hostname && (
              <span className="flex items-center gap-1">
                <Globe className="h-3 w-3" />
                {session.hostname}
              </span>
            )}
            {session.ipAddress && (
              <span className="flex items-center gap-1">
                {session.ipAddress}
              </span>
            )}
            {deviceLabel && <span>{deviceLabel}</span>}
            {deviceDetail && <span className="text-muted-foreground/50">({deviceDetail})</span>}
            <span>
              Last active: {formatRelativeTime(session.lastActivityAt)}
            </span>
            <span>
              Expires: {new Date(session.expiresAt).toLocaleDateString()}
            </span>
          </div>
        </div>

        {/* Revoke button */}
        {session.active && (
          <Button
            variant="ghost"
            size="sm"
            className="shrink-0 text-destructive hover:text-destructive"
            onClick={(e) => { e.stopPropagation(); onRevoke(); }}
            disabled={isRevoking}
          >
            <ShieldOff className="h-3.5 w-3.5" />
          </Button>
        )}
      </div>

      {/* Expanded: token rotation timeline */}
      {expanded && (
        <div className="border-t border-dashed border-border/60 bg-muted/20 px-4 py-3">
          <SessionTimeline
            platformSlug={platformSlug}
            organisationId={organisationId}
            sessionId={session.sessionId}
          />
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Session timeline (token rotation history)
// ---------------------------------------------------------------------------

function SessionTimeline({
  platformSlug,
  organisationId,
  sessionId,
}: {
  platformSlug: string;
  organisationId: number;
  sessionId: string;
}) {
  const fetchTimeline = useCallback(
    () => sessionsApi.timeline(platformSlug, organisationId, sessionId),
    [platformSlug, organisationId, sessionId],
  );

  const { data: events, isLoading, isError } = useQuery<SessionTimelineEvent[]>({
    queryKey: [...queryKeys.orgSessions(organisationId), "timeline", sessionId],
    queryFn: fetchTimeline,
    staleTime: 30_000,
  });

  // Capture "now" once via a lazy initializer to keep the render pure
  // (must be before any early returns to satisfy hooks rules)
  const [now] = useState(() => Date.now());

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 py-2 text-xs text-muted-foreground">
        <RefreshCw className="h-3 w-3 animate-spin" />
        Loading timeline...
      </div>
    );
  }

  if (isError || !events || events.length === 0) {
    return (
      <div className="py-2 text-xs text-muted-foreground">No timeline data.</div>);
  }

  // Compute the full time span for proportional rendering
  const firstCreatedAt = new Date(events[0].createdAt).getTime();
  const lastEnd = Math.max(
    ...events.map((e) => {
      const end = e.revokedAt ? new Date(e.revokedAt).getTime() : now;
      return Math.max(end, new Date(e.expiresAt).getTime());
    }),
    now,
  );
  const totalSpan = lastEnd - firstCreatedAt || 1;

  return (
    <div className="space-y-3">
      {/* Header */}
      <div className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
        <Clock className="h-3.5 w-3.5" />
        Token rotation timeline
        <span className="text-muted-foreground/60">({events.length} token{events.length !== 1 ? "s" : ""})</span>
      </div>

      {/* Horizontal bar: each segment is a token's lifetime */}
      <div className="relative h-8 w-full rounded-md bg-muted/50 overflow-hidden">
        {events.map((event, i) => {
          const startMs = new Date(event.createdAt).getTime() - firstCreatedAt;
          const endMs = (event.revokedAt
            ? new Date(event.revokedAt).getTime()
            : event.active
              ? now
              : new Date(event.expiresAt).getTime()) - firstCreatedAt;
          const left = (startMs / totalSpan) * 100;
          const width = Math.max(((endMs - startMs) / totalSpan) * 100, 0.5);

          const color = event.active
            ? "bg-emerald-500/70"
            : event.evictedAt
              ? "bg-amber-500/50"
              : event.revokedAt
                ? "bg-red-400/60"
                : "bg-muted-foreground/30";

          const statusLabel = event.active
            ? "Active"
            : event.evictedAt
              ? "Evicted"
              : event.revokedAt
                ? "Revoked"
                : "Expired";

          return (
            <div
              key={i}
              className={`absolute top-0 h-full ${color} transition-opacity hover:opacity-80 group cursor-default`}
              style={{ left: `${left}%`, width: `${width}%` }}
              title={`Token ${i + 1}: ${statusLabel}\nCreated: ${new Date(event.createdAt).toLocaleString()}\nExpires: ${new Date(event.expiresAt).toLocaleString()}${event.revokedAt ? `\nRevoked: ${new Date(event.revokedAt).toLocaleString()}` : ""}`}
            >
              {/* Tooltip on hover */}
              <div className="pointer-events-none absolute bottom-full left-1/2 z-10 mb-1 hidden -translate-x-1/2 whitespace-nowrap rounded bg-foreground px-2 py-1 text-[10px] text-background shadow-md group-hover:block">
                <div className="font-medium">Token {i + 1} — {statusLabel}</div>
                <div>Created: {new Date(event.createdAt).toLocaleTimeString()}</div>
                <div>Expires: {new Date(event.expiresAt).toLocaleTimeString()}</div>
                {event.revokedAt && <div>Revoked: {new Date(event.revokedAt).toLocaleTimeString()}</div>}
              </div>
            </div>
          );
        })}

        {/* Now marker */}
        {(() => {
          const nowOffset = ((now - firstCreatedAt) / totalSpan) * 100;
          if (nowOffset >= 0 && nowOffset <= 100) {
            return (
              <div
                className="absolute top-0 h-full w-px bg-foreground/40"
                style={{ left: `${nowOffset}%` }}
              />
            );
          }
          return null;
        })()}
      </div>

      {/* Legend */}
      <div className="flex flex-wrap gap-x-4 gap-y-1 text-[10px] text-muted-foreground/70">
        <span className="flex items-center gap-1">
          <span className="inline-block h-2 w-2 rounded-sm bg-emerald-500/70" />
          Active token
        </span>
        <span className="flex items-center gap-1">
          <span className="inline-block h-2 w-2 rounded-sm bg-red-400/60" />
          Revoked
        </span>
        <span className="flex items-center gap-1">
          <span className="inline-block h-2 w-2 rounded-sm bg-amber-500/50" />
          Evicted
        </span>
        <span className="flex items-center gap-1">
          <span className="inline-block h-2 w-2 rounded-sm bg-muted-foreground/30" />
          Expired
        </span>
        <span className="flex items-center gap-1">
          <span className="inline-block h-px w-3 bg-foreground/40" />
          Now
        </span>
      </div>

      {/* Token detail list */}
      <div className="space-y-1">
        {events.map((event, i) => (
          <div key={i} className="flex items-center gap-3 text-[11px]">
            <span className="w-4 text-right font-mono text-muted-foreground/50">{i + 1}</span>
            <span className={
              event.active
                ? "text-emerald-600 dark:text-emerald-400"
                : event.revokedAt
                  ? "text-red-500"
                  : "text-muted-foreground"
            }>
              {event.active ? "●" : event.revokedAt ? "✕" : "○"}
            </span>
            <span className="text-muted-foreground/70">
              {new Date(event.createdAt).toLocaleString()} →
            </span>
            <span className="text-muted-foreground/50">
              {event.revokedAt
                ? `revoked ${new Date(event.revokedAt).toLocaleString()}`
                : event.evictedAt
                  ? `evicted ${new Date(event.evictedAt).toLocaleString()}`
                  : `expires ${new Date(event.expiresAt).toLocaleString()}`}
            </span>
            {event.hostname && (
              <span className="text-muted-foreground/40">{event.hostname}</span>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function StatCard({
  icon,
  label,
  value,
  color,
}: {
  icon: React.ReactNode;
  label: string;
  value: number;
  color: string;
}) {
  const colors: Record<string, string> = {
    emerald: "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400",
    blue: "bg-blue-500/10 text-blue-600 dark:text-blue-400",
    violet: "bg-violet-500/10 text-violet-600 dark:text-violet-400",
  };
  return (
    <Card>
      <CardContent className="flex items-center gap-3 p-4">
        <div
          className={`flex h-9 w-9 items-center justify-center rounded-lg ${colors[color] ?? colors.blue}`}
        >
          {icon}
        </div>
        <div>
          <p className="text-2xl font-semibold">{value}</p>
          <p className="text-xs text-muted-foreground">{label}</p>
        </div>
      </CardContent>
    </Card>
  );
}

interface DeviceInfo {
  type: "desktop" | "mobile" | "tablet" | "bot";
  browser: string;
  browserVersion: string;
  os: string;
  osVersion: string;
  device: string; // e.g. "iPhone 15", "Pixel 8", "MacBook"
  raw: string;
}

function parseDevice(userAgent: string | null): DeviceInfo {
  const empty: DeviceInfo = {
    type: "desktop", browser: "Unknown", browserVersion: "",
    os: "Unknown", osVersion: "", device: "", raw: userAgent ?? "",
  };
  if (!userAgent) return empty;
  const ua = userAgent;

  // --- Bot detection ---
  if (/bot|crawl|spider|slurp|archive/i.test(ua)) {
    return { ...empty, type: "bot", browser: "Bot" };
  }

  // --- Device type ---
  const isIPad = /iPad/.test(ua);
  const isIPhone = /iPhone/.test(ua);
  const isAndroid = /Android/.test(ua);
  const isMobile = isIPhone || isAndroid || /Mobile|webOS|BlackBerry|IEMobile|Opera Mini/i.test(ua);
  const isTablet = isIPad || (isAndroid && !/Mobile/.test(ua)) || /Tablet|Kindle|Silk/i.test(ua);
  const type: DeviceInfo["type"] = isTablet ? "tablet" : isMobile ? "mobile" : "desktop";

  // --- Browser ---
  let browser = "Unknown";
  let browserVersion = "";
  const browserPatterns: [RegExp, string][] = [
    [/Edg(?:e|A|iOS)?\/([\d.]+)/, "Edge"],
    [/OPR\/([\d.]+)/, "Opera"],
    [/SamsungBrowser\/([\d.]+)/, "Samsung"],
    [/Brave\/([\d.]+)/, "Brave"],
    [/Vivaldi\/([\d.]+)/, "Vivaldi"],
    [/FxiOS\/([\d.]+)/, "Firefox"],
    [/CriOS\/([\d.]+)/, "Chrome"],
    [/Chrome\/([\d.]+)/, "Chrome"],
    [/Version\/([\d.]+).*Safari/, "Safari"],
    [/Safari\/([\d.]+)/, "Safari"],
    [/Firefox\/([\d.]+)/, "Firefox"],
    [/MSIE ([\d.]+)|Trident\/.*rv:([\d.]+)/, "IE"],
  ];
  for (const [pattern, name] of browserPatterns) {
    const match = ua.match(pattern);
    if (match) {
      browser = name;
      browserVersion = (match[1] ?? match[2] ?? "").split(".").slice(0, 2).join(".");
      break;
    }
  }

  // --- OS ---
  let os = "Unknown";
  let osVersion = "";
  if (isIPhone || isIPad) {
    os = "iOS";
    const v = ua.match(/OS ([\d_]+)/);
    osVersion = v ? v[1].replace(/_/g, ".") : "";
  } else if (isAndroid) {
    os = "Android";
    const v = ua.match(/Android ([\d.]+)/);
    osVersion = v ? v[1] : "";
  } else if (/Windows/.test(ua)) {
    os = "Windows";
    const v = ua.match(/Windows NT ([\d.]+)/);
    const ntMap: Record<string, string> = {
      "10.0": "10/11", "6.3": "8.1", "6.2": "8", "6.1": "7",
    };
    osVersion = v ? (ntMap[v[1]] ?? v[1]) : "";
  } else if (/Mac OS X/.test(ua)) {
    os = "macOS";
    const v = ua.match(/Mac OS X ([\d_]+)/);
    osVersion = v ? v[1].replace(/_/g, ".") : "";
  } else if (/Linux/.test(ua)) {
    os = "Linux";
  } else if (/CrOS/.test(ua)) {
    os = "Chrome OS";
  }

  // --- Device model (mobile/tablet only) ---
  let device = "";
  if (isIPhone) {
    device = "iPhone";
    // Try to identify model from OS version pattern (iOS 17+ maps to newer devices)
  } else if (isIPad) {
    device = "iPad";
  } else if (isAndroid) {
    const model = ua.match(/; ([^;)]+) Build/);
    if (model) device = model[1].trim();
  } else if (isMobile) {
    // Generic mobile
    const model = ua.match(/(?:Samsung|Pixel|OnePlus|Xiaomi|Huawei|Motorola|Nokia)[^;)]*/i);
    if (model) device = model[0].trim();
  } else {
    // Desktop: guess platform
    if (/Mac/.test(ua)) device = "Mac";
    else if (/Windows/.test(ua)) device = "PC";
    else if (/Linux/.test(ua)) device = "Linux PC";
    else device = "";
  }

  return { type, browser, browserVersion, os, osVersion, device, raw: ua };
}

function formatRelativeTime(iso: string): string {
  const now = Date.now();
  const then = new Date(iso).getTime();
  const diffMs = now - then;
  if (diffMs < 60_000) return "just now";
  if (diffMs < 3_600_000) return `${Math.floor(diffMs / 60_000)}m ago`;
  if (diffMs < 86_400_000) return `${Math.floor(diffMs / 3_600_000)}h ago`;
  return `${Math.floor(diffMs / 86_400_000)}d ago`;
}
