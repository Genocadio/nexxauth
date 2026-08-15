"use client";

import { useState } from "react";
import { Building2, Plus } from "lucide-react";
import Link from "next/link";
import { OrganisationDialog } from "@/components/organisations/organisation-dialog";
import { EmptyState } from "@/components/shared/empty-state";
import { ErrorState } from "@/components/shared/error-state";
import { PageHeader } from "@/components/shared/page-header";
import { CardsSkeleton } from "@/components/shared/loading";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter } from "@/components/ui/card";
import { useOrganisations, usePlatformSlug } from "@/hooks/queries";
import { formatDate } from "@/lib/constants";

export default function OrganisationsPage() {
  const platformSlug = usePlatformSlug() ?? "";
  const organisations = useOrganisations();
  const [open, setOpen] = useState(false);

  return (
    <div>
      <PageHeader
        title="Organisations"
        description="Every organisation is an isolated tenant under your platform."
        actions={
          <Button onClick={() => setOpen(true)}>
            <Plus /> New organisation
          </Button>
        }
      />

      {organisations.isLoading ? (
        <CardsSkeleton count={3} />
      ) : organisations.isError ? (
        <ErrorState error={organisations.error} onRetry={() => organisations.refetch()} />
      ) : organisations.data && organisations.data.length > 0 ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {organisations.data.map((org) => (
            <Card key={org.id} className="flex flex-col transition-shadow hover:shadow-lg">
              <CardContent className="flex-1 p-5">
                <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                  <Building2 className="h-5 w-5" />
                </div>
                <Link href={`/console/organisations/${org.slug}`} className="text-base font-semibold hover:text-primary">
                  {org.name}
                </Link>
                <p className="mt-0.5 font-mono text-xs text-muted-foreground">{org.slug}</p>
                {org.description ? (
                  <p className="mt-2 line-clamp-2 text-sm text-muted-foreground">{org.description}</p>
                ) : null}
              </CardContent>
              <CardFooter className="flex items-center justify-between border-t px-5 py-3 text-xs text-muted-foreground">
                <span>
                  {org.useEmailAsUsername ? "Email as username" : "Username login"}
                </span>
                <span>Created {formatDate(org.createdAt)}</span>
              </CardFooter>
            </Card>
          ))}
        </div>
      ) : (
        <EmptyState
          icon={Building2}
          title="No organisations yet"
          description="Create your first organisation to start managing users, roles and security policies."
          action={
            <Button onClick={() => setOpen(true)}>
              <Plus /> New organisation
            </Button>
          }
        />
      )}

      <OrganisationDialog platformSlug={platformSlug} open={open} onOpenChange={setOpen} />
    </div>
  );
}
