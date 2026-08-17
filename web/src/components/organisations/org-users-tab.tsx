"use client";

import { useState } from "react";
import { Pencil, Trash2, UserPlus, Users } from "lucide-react";
import { OrgUserDialog } from "@/components/organisations/org-user-dialog";
import { EmptyState } from "@/components/shared/empty-state";
import { ErrorState } from "@/components/shared/error-state";
import { InitialsAvatar } from "@/components/shared/initials-avatar";
import { ConfirmDialog } from "@/components/shared/confirm-dialog";
import { TableSkeleton } from "@/components/shared/loading";
import { EnabledBadge, UserRoles } from "@/components/shared/status-badge";
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
import { useDeleteOrgUser } from "@/hooks/mutations";
import { useOrgRoles, useOrgUserFields, useOrgUsers } from "@/hooks/queries";
import { formatDate } from "@/lib/constants";
import { fullName, type OrganisationUserResponse } from "@/types/api";

interface OrgUsersTabProps {
  platformSlug: string;
  organisationId: number;
  useEmailAsUsername: boolean;
}

export function OrgUsersTab({ platformSlug, organisationId, useEmailAsUsername }: OrgUsersTabProps) {
  const users = useOrgUsers(organisationId);
  const roles = useOrgRoles(organisationId);
  const fields = useOrgUserFields(organisationId);

  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<OrganisationUserResponse | null>(null);
  const [deleting, setDeleting] = useState<OrganisationUserResponse | null>(null);
  const deleteMutation = useDeleteOrgUser(platformSlug, organisationId);

  const loading = users.isLoading;
  const error = users.error;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          {users.data ? `${users.data.length} user${users.data.length === 1 ? "" : "s"}` : "Users"} of this organisation
        </p>
        <Button size="sm" onClick={() => setCreateOpen(true)}>
          <UserPlus /> Add user
        </Button>
      </div>

      <Card>
        <CardContent className="p-0">
          {loading ? (
            <div className="p-4">
              <TableSkeleton rows={5} columns={4} />
            </div>
          ) : error ? (
            <div className="p-4">
              <ErrorState error={error} onRetry={() => users.refetch()} />
            </div>
          ) : users.data && users.data.length > 0 ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>User</TableHead>
                  <TableHead>Login</TableHead>
                  <TableHead>Roles</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="hidden lg:table-cell">Joined</TableHead>
                  <TableHead className="w-20" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {users.data.map((user) => (
                  <TableRow key={user.id}>
                    <TableCell>
                      <div className="flex items-center gap-3">
                        <InitialsAvatar name={fullName(user)} />
                        <div className="min-w-0">
                          <p className="truncate text-sm font-medium">{fullName(user)}</p>
                          <p className="truncate text-xs text-muted-foreground">
                            {user.email ?? user.username ?? `#${user.id}`}
                          </p>
                          {user.metadata && Object.keys(user.metadata).length > 0 ? (
                            <p className="truncate text-xs text-muted-foreground">
                              {Object.entries(user.metadata)
                                .map(([key, value]) => `${key}: ${value}`)
                                .join(" · ")}
                            </p>
                          ) : null}
                        </div>
                      </div>
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {useEmailAsUsername ? user.email ?? "—" : user.username ?? "—"}
                    </TableCell>
                    <TableCell>
                      <UserRoles roles={user.roles} />
                    </TableCell>
                    <TableCell>
                      <EnabledBadge enabled={user.enabled} />
                    </TableCell>
                    <TableCell className="hidden text-sm text-muted-foreground lg:table-cell">
                      {formatDate(user.createdAt)}
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label={`Edit ${fullName(user)}`}
                          onClick={() => setEditing(user)}
                        >
                          <Pencil />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label={`Delete ${fullName(user)}`}
                          className="text-destructive hover:text-destructive"
                          onClick={() => setDeleting(user)}
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
                icon={Users}
                title="No users in this organisation"
                description="Add users and assign them roles to grant access."
                action={
                  <Button size="sm" onClick={() => setCreateOpen(true)}>
                    <UserPlus /> Add user
                  </Button>
                }
              />
            </div>
          )}
        </CardContent>
      </Card>

      <OrgUserDialog
        platformSlug={platformSlug}
        organisationId={organisationId}
        open={createOpen}
        onOpenChange={setCreateOpen}
        roles={roles.data ?? []}
        fields={fields.data ?? []}
        useEmailAsUsername={useEmailAsUsername}
      />
      <OrgUserDialog
        platformSlug={platformSlug}
        organisationId={organisationId}
        open={!!editing}
        onOpenChange={(open) => !open && setEditing(null)}
        roles={roles.data ?? []}
        fields={fields.data ?? []}
        useEmailAsUsername={useEmailAsUsername}
        user={editing ?? undefined}
      />

      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(open) => !open && setDeleting(null)}
        title="Delete organisation user"
        description={
          deleting
            ? `${fullName(deleting)} will be removed from this organisation. This cannot be undone.`
            : ""
        }
        confirmLabel="Delete user"
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
