"use client";

import { useDocs } from "@/components/docs/docs-provider";
import { Endpoint } from "@/components/docs/endpoint";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Info } from "lucide-react";

export default function LoginPage() {
  const docs = useDocs();

  const loginFields = [];
  if (docs.identifiers.emailCanLogin) loginFields.push("email");
  if (docs.identifiers.usernameCanLogin) loginFields.push("username");
  if (docs.identifiers.phoneCanLogin) loginFields.push("phone");

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Login</h1>
        <p className="mt-2 text-muted-foreground">
          Authenticate an existing user and receive access tokens.
        </p>
      </div>

      <Alert>
        <Info className="h-4 w-4" />
        <AlertTitle>Your Configuration</AlertTitle>
        <AlertDescription>
          <p className="mb-2">Login identifiers (use one of):</p>
          <ul className="list-disc list-inside space-y-1">
            {loginFields.length > 0 ? (
              loginFields.map((field) => (
                <li key={field} className="capitalize">{field}</li>
              ))
            ) : (
              <li>No login identifiers configured</li>
            )}
          </ul>
          {docs.customFields.some((f) => f.loginEnabled) && (
            <>
              <p className="mt-2 mb-2">Custom login fields:</p>
              <ul className="list-disc list-inside space-y-1">
                {docs.customFields
                  .filter((f) => f.loginEnabled)
                  .map((field) => (
                    <li key={field.key}>{field.key}</li>
                  ))}
              </ul>
            </>
          )}
        </AlertDescription>
      </Alert>

      <Endpoint
        method="POST"
        path={`/${docs.organisation.platformSlug}/auth/login`}
        description="Login with credentials"
        auth="Public (with X-Client-Id header)"
        request={JSON.stringify(
          {
            organisationId: docs.organisation.id,
            identifier: "johndoe",
            identifierType: "USERNAME",
            password: "securePassword123",
          },
          null,
          2
        )}
        response={JSON.stringify(
          {
            accessToken: "eyJhbGciOiJSUzI1NiIs...",
            refreshToken: "dGhpcyBpcyBhIHJlZnJl...",
            tokenType: "Bearer",
            expiresInSeconds: docs.sessions.accessTokenTtlSeconds,
            user: {
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
          },
          null,
          2
        )}
      />

      <Card>
        <CardHeader>
          <CardTitle>Identifier Types</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground mb-4">
            The <code>identifierType</code> field tells the API which field you&apos;re using:
          </p>
          <div className="space-y-2">
            {docs.identifiers.usernameCanLogin && (
              <div className="flex items-center gap-2">
                <code className="text-sm">USERNAME</code>
                <span className="text-sm text-muted-foreground">— Login with username</span>
              </div>
            )}
            {docs.identifiers.emailCanLogin && (
              <div className="flex items-center gap-2">
                <code className="text-sm">EMAIL</code>
                <span className="text-sm text-muted-foreground">— Login with email</span>
              </div>
            )}
            {docs.identifiers.phoneCanLogin && (
              <div className="flex items-center gap-2">
                <code className="text-sm">PHONE</code>
                <span className="text-sm text-muted-foreground">— Login with phone number</span>
              </div>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
