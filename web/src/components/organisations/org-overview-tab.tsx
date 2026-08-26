"use client";

import { useState } from "react";
import { Building2, Pencil } from "lucide-react";
import { ErrorState } from "@/components/shared/error-state";
import { FormField } from "@/components/shared/form-field";
import { TableSkeleton } from "@/components/shared/loading";
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
import { useUpdateOrganisation } from "@/hooks/mutations";
import { useOrganisation, usePlatform } from "@/hooks/queries";
import { useForm } from "@/hooks/use-form";
import { resolvePlatformApiUrl } from "@/lib/api-url";
import { useBackendOrigin } from "@/lib/backend-url";
import { formatDate } from "@/lib/constants";
import { updateOrganisationSchema } from "@/lib/validation";

interface OrgOverviewTabProps {
  platformSlug: string;
  organisationId: number;
}

export function OrgOverviewTab({ platformSlug, organisationId }: OrgOverviewTabProps) {
  const org = useOrganisation(organisationId);
  const platform = usePlatform();
  const backendOrigin = useBackendOrigin();

  const [editOpen, setEditOpen] = useState(false);
  const update = useUpdateOrganisation(platformSlug, organisationId);

  // The project base URL is the platform URL; all org endpoints live under it.
  const projectUrl = resolvePlatformApiUrl(platform.data?.apiBaseUrl, platformSlug, backendOrigin);

  const form = useForm(updateOrganisationSchema, {
    name: org.data?.name ?? "",
    description: org.data?.description ?? "",
  });

  if (org.isLoading) return <TableSkeleton rows={4} columns={2} />;
  if (org.isError) return <ErrorState error={org.error} onRetry={() => org.refetch()} />;

  const data = org.data!;

  return (
    <div className="space-y-6">
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
        <CardContent className="space-y-3 text-sm">
          {projectUrl ? <UrlRow label="Project URL" value={projectUrl} /> : null}
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

function UrlRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 rounded-lg px-2 py-1.5 transition-colors hover:bg-muted/50">
      <span className="text-muted-foreground">{label}</span>
      <div className="flex min-w-0 items-center justify-end gap-1.5">
        <code className="max-w-[70%] truncate font-mono text-xs font-medium">{value}</code>
        <CopyButton value={value} label={`Copy ${label}`} className="shrink-0" />
      </div>
    </div>
  );
}
