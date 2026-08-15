"use client";

import { useState } from "react";
import { Pencil, Plus, Shield, Trash2 } from "lucide-react";
import { OrgRoleDialog } from "@/components/organisations/org-role-dialog";
import { EmptyState } from "@/components/shared/empty-state";
import { ErrorState } from "@/components/shared/error-state";
import { ConfirmDialog } from "@/components/shared/confirm-dialog";
import { TableSkeleton } from "@/components/shared/loading";
import { ToneBadge } from "@/components/shared/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { useDeleteOrgRole } from "@/hooks/mutations";
import { useOrgRoles } from "@/hooks/queries";
import { PERMISSION_META, type Permission } from "@/types/enums";
import type { OrganisationRoleResponse } from "@/types/api";

interface OrgRolesTabProps {
  platformSlug: string;
  organisationSlug: string;
}

export function OrgRolesTab({ platformSlug, organisationSlug }: OrgRolesTabProps) {
  const roles = useOrgRoles(organisationSlug);
  const deleteMutation = useDeleteOrgRole(platformSlug, organisationSlug);

  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<OrganisationRoleResponse | null>(null);
  const [deleting, setDeleting] = useState<OrganisationRoleResponse | null>(null);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          {roles.data ? `${roles.data.length} role${roles.data.length === 1 ? "" : "s"}` : "Roles"} · permissions are fixed by the app
        </p>
        <Button size="sm" onClick={() => setCreateOpen(true)}>
          <Plus /> New role
        </Button>
      </div>

      {roles.isLoading ? (
        <TableSkeleton rows={3} columns={2} />
      ) : roles.isError ? (
        <ErrorState error={roles.error} onRetry={() => roles.refetch()} />
      ) : roles.data && roles.data.length > 0 ? (
        <div className="grid gap-4 sm:grid-cols-2">
          {roles.data.map((role) => (
            <Card key={role.id}>
              <CardContent className="p-5">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
                      <Shield className="h-4 w-4" />
                    </div>
                    <div>
                      <p className="text-sm font-semibold">{role.name}</p>
                      <p className="text-xs text-muted-foreground">
                        {role.permissions.length} permission{role.permissions.length === 1 ? "" : "s"}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-1">
                    <Button variant="ghost" size="icon" aria-label={`Edit ${role.name}`} onClick={() => setEditing(role)}>
                      <Pencil />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={`Delete ${role.name}`}
                      className="text-destructive hover:text-destructive"
                      onClick={() => setDeleting(role)}
                    >
                      <Trash2 />
                    </Button>
                  </div>
                </div>
                <div className="mt-4 flex flex-wrap gap-1.5">
                  {role.permissions.length > 0 ? (
                    role.permissions.map((permission) => (
                      <ToneBadge key={permission} tone="secondary">
                        {permissionLabel(permission)}
                      </ToneBadge>
                    ))
                  ) : (
                    <span className="text-xs text-muted-foreground">No permissions</span>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      ) : (
        <EmptyState
          icon={Shield}
          title="No roles yet"
          description="Create roles to group permissions, then assign them to users."
          action={
            <Button size="sm" onClick={() => setCreateOpen(true)}>
              <Plus /> New role
            </Button>
          }
        />
      )}

      <OrgRoleDialog
        platformSlug={platformSlug}
        organisationSlug={organisationSlug}
        open={createOpen}
        onOpenChange={setCreateOpen}
      />
      <OrgRoleDialog
        platformSlug={platformSlug}
        organisationSlug={organisationSlug}
        open={!!editing}
        onOpenChange={(open) => !open && setEditing(null)}
        role={editing ?? undefined}
      />

      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(open) => !open && setDeleting(null)}
        title="Delete role"
        description={
          deleting
            ? `The role "${deleting.name}" will be removed. Users holding it lose those permissions.`
            : ""
        }
        confirmLabel="Delete role"
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

function permissionLabel(permission: Permission): string {
  return PERMISSION_META[permission].label;
}
