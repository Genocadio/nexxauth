"use client";

import { useState } from "react";
import { Pencil, Plus, Shield, Trash2, CheckCircle2 } from "lucide-react";
import { OrgRoleDialog } from "@/components/organisations/org-role-dialog";
import { EmptyState } from "@/components/shared/empty-state";
import { ErrorState } from "@/components/shared/error-state";
import { ConfirmDialog } from "@/components/shared/confirm-dialog";
import { TableSkeleton } from "@/components/shared/loading";
import { ToneBadge } from "@/components/shared/status-badge";
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
import { useDeleteOrgRole } from "@/hooks/mutations";
import { useOrgRoles } from "@/hooks/queries";
import { ALL_PERMISSIONS, PERMISSION_META, type Permission } from "@/types/enums";
import type { OrganisationRoleResponse } from "@/types/api";

interface OrgRolesTabProps {
  platformSlug: string;
  organisationId: number;
}

export function OrgRolesTab({ platformSlug, organisationId }: OrgRolesTabProps) {
  const roles = useOrgRoles(organisationId);
  const deleteMutation = useDeleteOrgRole(platformSlug, organisationId);

  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<OrganisationRoleResponse | null>(null);
  const [deleting, setDeleting] = useState<OrganisationRoleResponse | null>(null);

  const loading = roles.isLoading;
  const error = roles.error;

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-muted-foreground">
          {roles.data ? `${roles.data.length} role${roles.data.length === 1 ? "" : "s"}` : "Roles"} · permissions are fixed by the app · default roles are auto-assigned to new users on registration
        </p>
        <Button size="sm" onClick={() => setCreateOpen(true)} className="self-start sm:self-auto">
          <Plus /> New role
        </Button>
      </div>

      <Card>
        <CardContent className="p-0">
          {loading ? (
            <div className="p-4">
              <TableSkeleton rows={4} columns={4} />
            </div>
          ) : error ? (
            <div className="p-4">
              <ErrorState error={error} onRetry={() => roles.refetch()} />
            </div>
          ) : roles.data && roles.data.length > 0 ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[200px]">Role</TableHead>
                  <TableHead className="w-[120px]">Type</TableHead>
                  <TableHead>Permissions</TableHead>
                  <TableHead className="w-20 text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {roles.data.map((role) => {
                  const isAllPermissions =
                    role.permissions.length > 0 &&
                    role.permissions.length === ALL_PERMISSIONS.length;

                  return (
                    <TableRow key={role.id}>
                      <TableCell>
                        <div className="flex items-center gap-2.5">
                          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                            <Shield className="h-4 w-4" />
                          </div>
                          <div className="min-w-0">
                            <p className="truncate text-sm font-semibold">{role.name}</p>
                            <p className="text-xs text-muted-foreground">ID #{role.id}</p>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        {role.isDefault ? (
                          <ToneBadge tone="success" className="gap-1">
                            <CheckCircle2 className="h-3 w-3" /> Default
                          </ToneBadge>
                        ) : (
                          <span className="text-xs text-muted-foreground">Custom</span>
                        )}
                      </TableCell>
                      <TableCell>
                        <div className="flex flex-wrap items-center gap-1.5 py-1">
                          {isAllPermissions ? (
                            <ToneBadge
                              tone="secondary"
                              className="border-primary/20 bg-primary/10 font-medium text-primary"
                            >
                              All Permissions ({role.permissions.length})
                            </ToneBadge>
                          ) : role.permissions.length > 0 ? (
                            role.permissions.map((permission) => (
                              <ToneBadge key={permission} tone="secondary" className="text-xs font-normal">
                                {permissionLabel(permission)}
                              </ToneBadge>
                            ))
                          ) : (
                            <span className="text-xs italic text-muted-foreground">
                              No permissions granted
                            </span>
                          )}
                        </div>
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex items-center justify-end gap-1">
                          <Button
                            variant="ghost"
                            size="icon"
                            aria-label={`Edit ${role.name}`}
                            onClick={() => setEditing(role)}
                          >
                            <Pencil className="h-4 w-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            aria-label={`Delete ${role.name}`}
                            className="text-destructive hover:text-destructive hover:bg-destructive/10"
                            onClick={() => setDeleting(role)}
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          ) : (
            <div className="p-6">
              <EmptyState
                icon={Shield}
                title="No roles yet"
                description="Create roles to group permissions, then assign them to users. Mark a role as default to auto-assign it to new users."
                action={
                  <Button size="sm" onClick={() => setCreateOpen(true)}>
                    <Plus /> New role
                  </Button>
                }
              />
            </div>
          )}
        </CardContent>
      </Card>

      <OrgRoleDialog
        platformSlug={platformSlug}
        organisationId={organisationId}
        open={createOpen}
        onOpenChange={setCreateOpen}
      />
      <OrgRoleDialog
        platformSlug={platformSlug}
        organisationId={organisationId}
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
  return PERMISSION_META[permission]?.label ?? permission;
}
