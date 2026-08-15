"use client";

import { useState } from "react";
import { Fingerprint, Pencil, Plus, Trash2 } from "lucide-react";
import { OrgFieldDialog } from "@/components/organisations/org-field-dialog";
import { EmptyState } from "@/components/shared/empty-state";
import { ErrorState } from "@/components/shared/error-state";
import { ConfirmDialog } from "@/components/shared/confirm-dialog";
import { TableSkeleton } from "@/components/shared/loading";
import { UserFieldTypeBadge } from "@/components/shared/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useDeleteUserField } from "@/hooks/mutations";
import { useOrgUserFields } from "@/hooks/queries";
import type { OrganisationUserFieldResponse } from "@/types/api";

interface OrgFieldsTabProps {
  platformSlug: string;
  organisationSlug: string;
}

export function OrgFieldsTab({ platformSlug, organisationSlug }: OrgFieldsTabProps) {
  const fields = useOrgUserFields(organisationSlug);
  const deleteMutation = useDeleteUserField(platformSlug, organisationSlug);

  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<OrganisationUserFieldResponse | null>(null);
  const [deleting, setDeleting] = useState<OrganisationUserFieldResponse | null>(null);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          Extra attributes stored on every user, returned as <code className="font-mono">metadata</code>.
        </p>
        <Button size="sm" onClick={() => setCreateOpen(true)}>
          <Plus /> New field
        </Button>
      </div>

      <Card>
        <CardContent className="p-0">
          {fields.isLoading ? (
            <div className="p-4">
              <TableSkeleton rows={4} columns={3} />
            </div>
          ) : fields.isError ? (
            <div className="p-4">
              <ErrorState error={fields.error} onRetry={() => fields.refetch()} />
            </div>
          ) : fields.data && fields.data.length > 0 ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Field</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Login identifier</TableHead>
                  <TableHead className="w-20" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {fields.data.map((field) => (
                  <TableRow key={field.id}>
                    <TableCell>
                      <p className="text-sm font-medium">{field.label}</p>
                      <p className="font-mono text-xs text-muted-foreground">{field.key}</p>
                    </TableCell>
                    <TableCell>
                      <UserFieldTypeBadge fieldType={field.fieldType} />
                    </TableCell>
                    <TableCell>
                      {field.loginEnabled ? (
                        <span className="inline-flex items-center gap-1.5 text-sm">
                          <Fingerprint className="h-3.5 w-3.5 text-primary" />
                          Can be used to log in
                        </span>
                      ) : (
                        <span className="text-sm text-muted-foreground">No</span>
                      )}
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label={`Edit ${field.label}`}
                          onClick={() => setEditing(field)}
                        >
                          <Pencil />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label={`Delete ${field.label}`}
                          className="text-destructive hover:text-destructive"
                          onClick={() => setDeleting(field)}
                        >
                          <Trash2 />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : (
            <div className="p-4">
              <EmptyState
                icon={Fingerprint}
                title="No user fields defined"
                description="Add custom attributes (e.g. employee id, department) that appear on every user."
                action={
                  <Button size="sm" onClick={() => setCreateOpen(true)}>
                    <Plus /> New field
                  </Button>
                }
              />
            </div>
          )}
        </CardContent>
      </Card>

      <OrgFieldDialog
        platformSlug={platformSlug}
        organisationSlug={organisationSlug}
        open={createOpen}
        onOpenChange={setCreateOpen}
      />
      <OrgFieldDialog
        platformSlug={platformSlug}
        organisationSlug={organisationSlug}
        open={!!editing}
        onOpenChange={(open) => !open && setEditing(null)}
        field={editing ?? undefined}
      />

      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(open) => !open && setDeleting(null)}
        title="Delete user field"
        description={
          deleting
            ? `"${deleting.label}" and every value stored under it will be removed. Users lose that metadata.`
            : ""
        }
        confirmLabel="Delete field"
        pending={deleteMutation.isPending}
        onConfirm={async () => {
          if (deleting) {
            await deleteMutation.mutateAsync(deleting.id);
            setDeleting(null);
          }
        }}
      />
    </div>
  );
}
