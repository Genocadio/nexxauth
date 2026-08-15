"use client";

import { useState } from "react";
import { Pencil, UserPlus, Users } from "lucide-react";
import { PlatformUserDialog } from "@/components/users/platform-user-dialog";
import { EmptyState } from "@/components/shared/empty-state";
import { ErrorState } from "@/components/shared/error-state";
import { InitialsAvatar } from "@/components/shared/initials-avatar";
import { PageHeader } from "@/components/shared/page-header";
import { TableSkeleton } from "@/components/shared/loading";
import { EnabledBadge, RoleBadge } from "@/components/shared/status-badge";
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
import { usePlatformUsers } from "@/hooks/queries";
import { usePlatformSlug } from "@/hooks/queries";
import { formatDate } from "@/lib/constants";
import { fullName, type PlatformUserResponse } from "@/types/api";

export default function PlatformUsersPage() {
  const platformSlug = usePlatformSlug() ?? "";
  const users = usePlatformUsers();

  const [addOpen, setAddOpen] = useState(false);
  const [editing, setEditing] = useState<PlatformUserResponse | null>(null);

  return (
    <div>
      <PageHeader
        title="Platform users"
        description="Members of your platform and their access level."
        actions={
          <Button onClick={() => setAddOpen(true)}>
            <UserPlus /> Add user
          </Button>
        }
      />

      <Card>
        <CardContent className="p-0">
          {users.isLoading ? (
            <div className="p-4">
              <TableSkeleton rows={5} columns={4} />
            </div>
          ) : users.isError ? (
            <div className="p-4">
              <ErrorState error={users.error} onRetry={() => users.refetch()} />
            </div>
          ) : users.data && users.data.length > 0 ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Member</TableHead>
                  <TableHead>Role</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="hidden md:table-cell">Phone</TableHead>
                  <TableHead className="hidden lg:table-cell">Joined</TableHead>
                  <TableHead className="w-12" />
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
                          <p className="truncate text-xs text-muted-foreground">{user.email}</p>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                      <RoleBadge role={user.role} />
                    </TableCell>
                    <TableCell>
                      <EnabledBadge enabled={user.enabled} />
                    </TableCell>
                    <TableCell className="hidden text-sm text-muted-foreground md:table-cell">
                      {user.phone || "—"}
                    </TableCell>
                    <TableCell className="hidden text-sm text-muted-foreground lg:table-cell">
                      {formatDate(user.createdAt)}
                    </TableCell>
                    <TableCell>
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={`Edit ${fullName(user)}`}
                        onClick={() => setEditing(user)}
                      >
                        <Pencil />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : (
            <div className="p-4">
              <EmptyState
                icon={Users}
                title="No users yet"
                description="Add your first platform member to share access to this platform."
                action={
                  <Button onClick={() => setAddOpen(true)}>
                    <UserPlus /> Add user
                  </Button>
                }
              />
            </div>
          )}
        </CardContent>
      </Card>

      <PlatformUserDialog
        platformSlug={platformSlug}
        open={addOpen}
        onOpenChange={setAddOpen}
      />
      <PlatformUserDialog
        platformSlug={platformSlug}
        open={!!editing}
        onOpenChange={(open) => !open && setEditing(null)}
        user={editing ?? undefined}
      />
    </div>
  );
}
