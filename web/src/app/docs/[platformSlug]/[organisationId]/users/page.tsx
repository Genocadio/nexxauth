"use client";

import { useDocs } from "@/components/docs/docs-provider";
import { Endpoint } from "@/components/docs/endpoint";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

export default function UsersPage() {
  const docs = useDocs();

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">User Management</h1>
        <p className="mt-2 text-muted-foreground">
          Manage users, roles, and custom fields in your organisation.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Your Permissions</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground mb-2">
            Available permissions in this organisation:
          </p>
          <div className="flex flex-wrap gap-2">
            {docs.availablePermissions.map((perm) => (
              <Badge key={perm} variant="outline">
                {perm}
              </Badge>
            ))}
          </div>
        </CardContent>
      </Card>

      <Endpoint
        method="GET"
        path="/organisations/users"
        description="List all users"
        auth="SERVER client or platform user with ORGANISATION_USER_READ permission"
        response={JSON.stringify(
          [
            {
              id: 1,
              firstName: "John",
              lastName: "Doe",
              username: "johndoe",
              email: "john@example.com",
              phone: null,
              enabled: true,
              authTypes: ["PASSWORD"],
              roles: ["User"],
              createdAt: "2024-01-01T00:00:00Z",
              metadata: docs.customFields.length > 0
                ? Object.fromEntries(
                    docs.customFields.map((f) => [f.key, "value"])
                  )
                : {},
            },
          ],
          null,
          2
        )}
      />

      <Endpoint
        method="GET"
        path="/organisations/users/me"
        description="Get current user profile"
        auth="Any authenticated org user"
        response={JSON.stringify(
          {
            id: 1,
            firstName: "John",
            lastName: "Doe",
            username: "johndoe",
            email: "john@example.com",
            phone: null,
            enabled: true,
            authTypes: ["PASSWORD"],
            roles: ["User"],
            createdAt: "2024-01-01T00:00:00Z",
            metadata: {},
          },
          null,
          2
        )}
      />

      <Endpoint
        method="PATCH"
        path="/organisations/users/me"
        description="Update own profile"
        auth="Any authenticated org user"
        request={JSON.stringify(
          {
            firstName: "John",
            lastName: "Doe Updated",
            ...(docs.customFields.length > 0 && {
              metadata: Object.fromEntries(
                docs.customFields.map((f) => [f.key, "new-value"])
              ),
            }),
          },
          null,
          2
        )}
      />
    </div>
  );
}
