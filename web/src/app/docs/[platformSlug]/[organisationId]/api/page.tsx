"use client";

import { useDocs } from "@/components/docs/docs-provider";
import { useDocsApiBaseUrl } from "@/hooks/use-docs-api-url";
import { Endpoint } from "@/components/docs/endpoint";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";

export default function ApiReferencePage() {
  const docs = useDocs();
  const baseUrl = useDocsApiBaseUrl(docs.organisation.platformSlug);
  const basePath = "/organisations";

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">API Reference</h1>
        <p className="mt-2 text-muted-foreground">
          Complete endpoint reference for {docs.organisation.name}.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Base URL</CardTitle>
        </CardHeader>
        <CardContent>
          <code className="text-sm">{baseUrl}</code>
        </CardContent>
      </Card>

      <Separator />

      <div>
        <h2 className="text-xl font-semibold mb-4">Authentication Endpoints</h2>
        <div className="space-y-4">
          <Endpoint
            method="POST"
            path="/auth/register"
            description="Register a new user"
            auth="Public (with X-Client-Id header)"
          />
          <Endpoint
            method="POST"
            path="/auth/login"
            description="Login with credentials"
            auth="Public (with X-Client-Id header)"
          />
          <Endpoint
            method="POST"
            path="/auth/refresh"
            description="Refresh access token"
            auth="Public (with X-Client-Id header)"
          />
          <Endpoint
            method="POST"
            path="/auth/logout"
            description="Logout and invalidate refresh token"
            auth="Public (with X-Client-Id header)"
          />
        </div>
      </div>

      <Separator />

      <div>
        <h2 className="text-xl font-semibold mb-4">User Endpoints</h2>
        <div className="space-y-4">
          <Endpoint
            method="GET"
            path={`${basePath}/users`}
            description="List all users"
            auth="SERVER client or ORGANISATION_USER_READ"
          />
          <Endpoint
            method="GET"
            path={`${basePath}/users/me`}
            description="Get current user profile"
            auth="Any authenticated org user"
          />
          <Endpoint
            method="PATCH"
            path={`${basePath}/users/me`}
            description="Update own profile"
            auth="Any authenticated org user"
          />
          <Endpoint
            method="POST"
            path={`${basePath}/users/me/change-password`}
            description="Change own password"
            auth="Any authenticated org user"
          />
          <Endpoint
            method="POST"
            path={`${basePath}/users`}
            description="Create a new user"
            auth="SERVER client or ORGANISATION_USER_CREATE"
          />
          <Endpoint
            method="GET"
            path={`${basePath}/users/{userId}`}
            description="Get a specific user"
            auth="SERVER client or ORGANISATION_USER_READ"
          />
          <Endpoint
            method="PATCH"
            path={`${basePath}/users/{userId}`}
            description="Update a user"
            auth="SERVER client or ORGANISATION_USER_UPDATE"
          />
          <Endpoint
            method="DELETE"
            path={`${basePath}/users/{userId}`}
            description="Delete a user"
            auth="SERVER client or ORGANISATION_USER_DELETE"
          />
        </div>
      </div>

      <Separator />

      <div>
        <h2 className="text-xl font-semibold mb-4">Role Endpoints</h2>
        <div className="space-y-4">
          <Endpoint
            method="GET"
            path={`${basePath}/roles`}
            description="List all roles"
            auth="SERVER client or ORGANISATION_USER_READ"
          />
          <Endpoint
            method="POST"
            path={`${basePath}/roles`}
            description="Create a new role"
            auth="PLATFORM SUPER_USER only"
          />
          <Endpoint
            method="GET"
            path={`${basePath}/roles/{roleId}`}
            description="Get a specific role"
            auth="SERVER client or ORGANISATION_USER_READ"
          />
          <Endpoint
            method="PATCH"
            path={`${basePath}/roles/{roleId}`}
            description="Update a role"
            auth="PLATFORM SUPER_USER only"
          />
          <Endpoint
            method="DELETE"
            path={`${basePath}/roles/{roleId}`}
            description="Delete a role"
            auth="PLATFORM SUPER_USER only"
          />
        </div>
      </div>

      <Separator />

      <div>
        <h2 className="text-xl font-semibold mb-4">Custom Field Endpoints</h2>
        <div className="space-y-4">
          <Endpoint
            method="GET"
            path={`${basePath}/user-fields`}
            description="List all custom fields"
            auth="SERVER client or ORGANISATION_USER_FIELD_READ"
          />
          <Endpoint
            method="POST"
            path={`${basePath}/user-fields`}
            description="Create a custom field"
            auth="PLATFORM SUPER_USER or ORGANISATION_USER_FIELD_CREATE"
          />
          <Endpoint
            method="PATCH"
            path={`${basePath}/user-fields/{fieldId}`}
            description="Update a custom field"
            auth="PLATFORM SUPER_USER or ORGANISATION_USER_FIELD_UPDATE"
          />
          <Endpoint
            method="DELETE"
            path={`${basePath}/user-fields/{fieldId}`}
            description="Delete a custom field"
            auth="PLATFORM SUPER_USER or ORGANISATION_USER_FIELD_DELETE"
          />
        </div>
      </div>

      <Separator />

      <div>
        <h2 className="text-xl font-semibold mb-4">Configuration Endpoints</h2>
        <div className="space-y-4">
          <Endpoint
            method="GET"
            path={`${basePath}/auth-config`}
            description="Get auth configuration"
            auth="SERVER client or any org user"
          />
          <Endpoint
            method="PATCH"
            path={`${basePath}/auth-config`}
            description="Update auth configuration"
            auth="PLATFORM SUPER_USER only"
          />
          <Endpoint
            method="GET"
            path={`${basePath}/session-settings`}
            description="Get session settings"
            auth="SERVER client or any org user"
          />
          <Endpoint
            method="PATCH"
            path={`${basePath}/session-settings`}
            description="Update session settings"
            auth="PLATFORM SUPER_USER only"
          />
        </div>
      </div>

      <Separator />

      <div>
        <h2 className="text-xl font-semibold mb-4">Key Endpoints</h2>
        <div className="space-y-4">
          <Endpoint
            method="GET"
            path={`${basePath}/keys`}
            description="Get public keys for token verification"
            auth="Public"
          />
          <Endpoint
            method="POST"
            path={`${basePath}/keys/rotate`}
            description="Rotate signing keys"
            auth="PLATFORM SUPER_USER only"
          />
        </div>
      </div>
    </div>
  );
}
