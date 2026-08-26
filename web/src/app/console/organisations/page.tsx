"use client";

import { useState } from "react";
import { Building2, Plus, Rocket } from "lucide-react";
import Link from "next/link";
import { motion } from "framer-motion";
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

      {platformSlug && (organisations.data ?? []).some((o) => (o.onboardingStep ?? 0) < 8) ? (
        <Link
          href={`/console/onboarding/${platformSlug}`}
          className="mb-6 flex items-center justify-between gap-4 rounded-xl border border-primary/20 bg-gradient-to-r from-primary/5 to-primary/10 p-4 transition-all duration-200 hover:shadow-md hover:shadow-primary/5"
        >
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <Rocket className="h-4.5 w-4.5" />
            </div>
            <div>
              <p className="text-sm font-medium">Finish setting up your organisation</p>
              <p className="text-xs text-muted-foreground">
                Continue the setup wizard — identifiers, fields, auth, sessions, client and keys.
              </p>
            </div>
          </div>
          <span className="text-sm font-medium text-primary">Continue →</span>
        </Link>
      ) : null}

      {organisations.isLoading ? (
        <CardsSkeleton count={3} />
      ) : organisations.isError ? (
        <ErrorState error={organisations.error} onRetry={() => organisations.refetch()} />
      ) : organisations.data && organisations.data.length > 0 ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {organisations.data.map((org, i) => (
            <motion.div
              key={org.id}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.05, duration: 0.3 }}
            >
              <Card className="group flex h-full flex-col transition-all duration-200 hover:shadow-xl hover:shadow-primary/5 hover:-translate-y-0.5">
                <CardContent className="flex-1 p-5">
                  <div className="mb-3 flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-primary/10 to-primary/5 text-primary transition-transform duration-200 group-hover:scale-110">
                    <Building2 className="h-5 w-5" />
                  </div>
                  <Link
                    href={`/console/organisations/${org.slug}`}
                    className="text-base font-semibold transition-colors hover:text-primary"
                  >
                    {org.name}
                  </Link>
                  <p className="mt-0.5 font-mono text-xs text-muted-foreground">{org.slug}</p>
                  {org.description ? (
                    <p className="mt-2 line-clamp-2 text-sm text-muted-foreground">{org.description}</p>
                  ) : null}
                </CardContent>
                <CardFooter className="flex items-center justify-between border-t px-5 py-3 text-xs text-muted-foreground">
                  <span className="flex items-center gap-1.5">
                    <span
                      className={`h-1.5 w-1.5 rounded-full ${
                        org.useEmailAsUsername ? "bg-emerald-500" : "bg-blue-500"
                      }`}
                    />
                    {org.useEmailAsUsername ? "Email as username" : "Username login"}
                  </span>
                  <span>Created {formatDate(org.createdAt)}</span>
                </CardFooter>
              </Card>
            </motion.div>
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
