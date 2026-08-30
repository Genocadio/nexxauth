"use client";

import { useDocs } from "@/components/docs/docs-provider";
import { Endpoint } from "@/components/docs/endpoint";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";

export default function RolesPage() {
  const docs = useDocs();

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Roles</h1>
        <p className="mt-2 text-muted-foreground">
          Manage roles and permissions for your organisation.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Your Roles</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {docs.roles.length === 0 ? (
            <p className="text-sm text-muted-foreground">No roles configured</p>
          ) : (
            docs.roles.map((role) => (
              <div key={role.id} className="border rounded-lg p-4">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <span className="font-medium">{role.name}</span>
                    {role.isDefault && (
                      <Badge variant="secondary">Default</Badge>
                    )}
                  </div>
                </div>
                <div className="flex flex-wrap gap-1">
                  {role.permissions.length === 0 ? (
                    <span className="text-xs text-muted-foreground">No permissions</span>
                  ) : (
                    role.permissions.map((perm) => (
                      <Badge key={perm} variant="outline" className="text-xs">
                        {perm}
                      </Badge>
                    ))
                  )}
                </div>
              </div>
            ))
          )}
        </CardContent>
      </Card>

      <Separator />

      <Endpoint
        method="GET"
        path={`/organisations/roles`}
        description="List all roles"
        auth="SERVER client or platform user with ORGANISATION_USER_READ permission"
        response={JSON.stringify(
          docs.roles.map((role) => ({
            id: role.id,
            name: role.name,
            permissions: role.permissions,
            isDefault: role.isDefault,
          })),
          null,
          2
        )}
      />

      <Endpoint
        method="POST"
        path={`/organisations/roles`}
        description="Create a new role"
        auth="PLATFORM SUPER_USER only"
        request={JSON.stringify(
          {
            name: "Editor",
            permissions: ["ORGANISATION_USER_READ", "ORGANISATION_USER_UPDATE"],
            isDefault: false,
          },
          null,
          2
        )}
      />

      <Endpoint
        method="PATCH"
        path={`/organisations/roles/{roleId}`}
        description="Update a role"
        auth="PLATFORM SUPER_USER only"
        request={JSON.stringify(
          {
            name: "Senior Editor",
            permissions: [
              "ORGANISATION_USER_READ",
              "ORGANISATION_USER_UPDATE",
              "ORGANISATION_USER_DELETE",
            ],
          },
          null,
          2
        )}
      />

      <Endpoint
        method="DELETE"
        path={`/organisations/roles/{roleId}`}
        description="Delete a role"
        auth="PLATFORM SUPER_USER only"
      />
    </div>
  );
}
