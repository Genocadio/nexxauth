"use client";

import { Building2, CalendarDays, ShieldCheck, Users } from "lucide-react";
import Link from "next/link";
import { FadeIn } from "@/components/shared/fade-in";
import { PageHeader } from "@/components/shared/page-header";
import { CardsSkeleton, TableSkeleton } from "@/components/shared/loading";
import { StatCard } from "@/components/shared/stat-card";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { CopyButton } from "@/components/shared/copy-button";
import { useOrganisations, usePlatform, usePlatformUsers } from "@/hooks/queries";
import { resolvePlatformApiUrl } from "@/lib/api-url";
import { formatDate } from "@/lib/constants";
import { ROLE_META } from "@/types/enums";

export default function OverviewPage() {
  const platform = usePlatform();
  const users = usePlatformUsers();
  const organisations = useOrganisations();

  const loading = platform.isLoading || users.isLoading || organisations.isLoading;
  const error = platform.error ?? users.error ?? organisations.error;

  const platformData = platform.data;
  const userCount = platformData?.userCount ?? users.data?.length ?? 0;
  const orgCount = organisations.data?.length ?? 0;
  const apiUrl = platformData ? resolvePlatformApiUrl(platformData.apiBaseUrl, platformData.slug) : null;

  return (
    <div className="space-y-8">
      <PageHeader
        title={platformData?.name ?? "Platform"}
        description={
          platformData
            ? `Platform ${platformData.slug} — created ${formatDate(platformData.createdAt)}`
            : "Your platform overview"
        }
        actions={
          <Button asChild>
            <Link href="/console/organisations">New organisation</Link>
          </Button>
        }
      />

      {loading ? (
        <CardsSkeleton />
      ) : error ? (
        <p className="text-sm text-destructive">Couldn&apos;t load the overview.</p>
      ) : (
        <FadeIn delay={0.05}>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <StatCard icon={Building2} label="Organisations" value={orgCount} sub="under this platform" />
            <StatCard icon={Users} label="Platform users" value={userCount} sub="members with access" />
            <StatCard icon={ShieldCheck} label="Your role" value={platformData ? "Admin" : "—"} sub="full access to everything" />
          </div>
        </FadeIn>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <FadeIn delay={0.1}>
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Platform details</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4 text-sm">
              {apiUrl ? (
                <>
                  <UrlRow label="API URL" value={apiUrl} />
                  <Separator />
                </>
              ) : null}
              <Row label="Name" value={platformData?.name} />
              <Row label="Slug" value={platformData?.slug} mono />
              <Row label="Created" value={platformData ? formatDate(platformData.createdAt) : undefined} />
              <Separator />
              <Row
                label="First user"
                value={users.data?.[0] ? `${users.data[0].firstName} ${users.data[0].lastName}` : undefined}
              />
              <Row
                label="Your permissions"
                value={ROLE_META.SUPER_USER.description}
                muted
              />
            </CardContent>
          </Card>
        </FadeIn>

        <FadeIn delay={0.15}>
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Recent organisations</CardTitle>
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
                        className="flex items-center justify-between gap-3 py-3 transition-colors hover:text-primary"
                      >
                        <div className="min-w-0">
                          <p className="truncate text-sm font-medium">{org.name}</p>
                          <p className="truncate text-xs text-muted-foreground">{org.slug}</p>
                        </div>
                        <span className="flex shrink-0 items-center gap-1.5 text-xs text-muted-foreground">
                          <CalendarDays className="h-3.5 w-3.5" />
                          {formatDate(org.createdAt)}
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              ) : (
                <div className="py-8 text-center text-sm text-muted-foreground">
                  No organisations yet — create your first one to get started.
                </div>
              )}
            </CardContent>
          </Card>
        </FadeIn>
      </div>
    </div>
  );
}

function Row({ label, value, mono, muted }: { label: string; value?: string; mono?: boolean; muted?: boolean }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <span className="text-muted-foreground">{label}</span>
      <span
        className={`max-w-[60%] truncate text-right font-medium ${mono ? "font-mono text-xs" : ""} ${muted ? "text-muted-foreground" : ""}`}
      >
        {value ?? "—"}
      </span>
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
