"use client";

import { useDocs } from "@/components/docs/docs-provider";
import { Endpoint } from "@/components/docs/endpoint";
import { CodeBlock } from "@/components/docs/code-block";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Info } from "lucide-react";

export default function RegisterPage() {
  const docs = useDocs();

  const requiredFields = [];
  if (docs.identifiers.emailRequired) requiredFields.push("email");
  if (docs.identifiers.usernameRequired) requiredFields.push("username");
  if (docs.identifiers.phoneRequired) requiredFields.push("phone");

  const loginFields = [];
  if (docs.identifiers.emailCanLogin) loginFields.push("email");
  if (docs.identifiers.usernameCanLogin) loginFields.push("username");
  if (docs.identifiers.phoneCanLogin) loginFields.push("phone");

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Register</h1>
        <p className="mt-2 text-muted-foreground">
          Create a new user account in the organisation.
        </p>
      </div>

      <Alert>
        <Info className="h-4 w-4" />
        <AlertTitle>Your Configuration</AlertTitle>
        <AlertDescription>
          <p className="mb-2">This organisation requires:</p>
          <ul className="list-disc list-inside space-y-1">
            {requiredFields.length > 0 ? (
              requiredFields.map((field) => (
                <li key={field} className="capitalize">{field}</li>
              ))
            ) : (
              <li>No required fields (any identifier is optional)</li>
            )}
          </ul>
          <p className="mt-2 mb-2">Login identifiers:</p>
          <ul className="list-disc list-inside space-y-1">
            {loginFields.length > 0 ? (
              loginFields.map((field) => (
                <li key={field} className="capitalize">{field}</li>
              ))
            ) : (
              <li>No login identifiers configured</li>
            )}
          </ul>
        </AlertDescription>
      </Alert>

      <Endpoint
        method="POST"
        path={`/${docs.organisation.platformSlug}/auth/register`}
        description="Register a new user"
        auth="Public (with X-Client-Id header)"
        request={JSON.stringify(
          {
            organisationId: docs.organisation.id,
            firstName: "John",
            lastName: "Doe",
            ...(docs.identifiers.usernameRequired && { username: "johndoe" }),
            ...(docs.identifiers.emailRequired && { email: "john@example.com" }),
            ...(docs.identifiers.phoneRequired && { phone: "+1234567890" }),
            password: "securePassword123",
            ...(docs.customFields.length > 0 && {
              metadata: Object.fromEntries(
                docs.customFields.map((field) => [field.key, "value"])
              ),
            }),
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
              username: docs.identifiers.usernameRequired ? "johndoe" : null,
              email: docs.identifiers.emailRequired ? "john@example.com" : null,
              phone: docs.identifiers.phoneRequired ? "+1234567890" : null,
              enabled: true,
              authType: "PASSWORD",
              roles: docs.roles
                .filter((r) => r.isDefault)
                .map((r) => ({ id: r.id, name: r.name })),
              createdAt: "2024-01-01T00:00:00Z",
              metadata: docs.customFields.length > 0
                ? Object.fromEntries(
                    docs.customFields.map((field) => [field.key, "value"])
                  )
                : null,
            },
          },
          null,
          2
        )}
      />

      <Card>
        <CardHeader>
          <CardTitle>Using the Client</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-muted-foreground">
            Include the <code>X-Client-Id</code> header with your client key:
          </p>
          <CodeBlock
            code={`curl -X POST https://your-api-domain.com/${docs.organisation.platformSlug}/auth/register \\
  -H "Content-Type: application/json" \\
  -H "X-Client-Id: cli_your_client_key" \\
  -d '${JSON.stringify(
    {
      organisationId: docs.organisation.id,
      firstName: "John",
      lastName: "Doe",
      password: "securePassword123",
    },
    null,
    2
  )}'`}
            language="bash"
          />
        </CardContent>
      </Card>
    </div>
  );
}
