"use client";

import { useDocs } from "@/components/docs/docs-provider";
import { useDocsApiBaseUrl } from "@/hooks/use-docs-api-url";
import { CodeBlock } from "@/components/docs/code-block";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";

export default function QuickStartPage() {
  const docs = useDocs();
  const baseUrl = useDocsApiBaseUrl(docs.organisation.platformSlug);

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Quick Start</h1>
        <p className="mt-2 text-muted-foreground">
          Get up and running with your first API call in minutes.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>1. Get Your Client Credentials</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-muted-foreground">
            From the console, go to <strong>Clients</strong> and create a new client.
            You&apos;ll receive:
          </p>
          <ul className="list-disc list-inside space-y-1 text-sm text-muted-foreground">
            <li><code>clientKey</code> — sent as <code>X-Client-Id</code> header</li>
            <li><code>token</code> — static token for SERVER clients (shown once)</li>
          </ul>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>2. Register a User</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <CodeBlock
            code={`curl -X POST ${baseUrl}/auth/register \\
  -H "Content-Type: application/json" \\
  -H "X-Client-Id: cli_your_client_key" \\
  -d '${JSON.stringify(
    {
      firstName: "John",
      lastName: "Doe",
      password: "securePassword123",
      ...(docs.identifiers.usernameRequired && { username: "johndoe" }),
      ...(docs.identifiers.emailRequired && { email: "john@example.com" }),
    },
    null,
    2
  )}'`}
            language="bash"
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>3. Login</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <CodeBlock
            code={`curl -X POST ${baseUrl}/auth/login \\
  -H "Content-Type: application/json" \\
  -H "X-Client-Id: cli_your_client_key" \\
  -d '${JSON.stringify(
    {
      identifier: "johndoe",
      identifierType: "USERNAME",
      password: "securePassword123",
    },
    null,
  )}'`}
            language="bash"
          />
          <p className="text-sm text-muted-foreground">
            Response includes <code>accessToken</code> and <code>refreshToken</code>.
          </p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>4. Use the Access Token</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <CodeBlock
            code={`curl -X GET ${baseUrl}/organisations/users/me \\
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIs..." \\
  -H "X-Client-Id: cli_your_client_key"`}
            language="bash"
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>5. Refresh the Token</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <CodeBlock
            code={`curl -X POST ${baseUrl}/auth/refresh \\
  -H "Content-Type: application/json" \\
  -H "X-Client-Id: cli_your_client_key" \\
  -d '${JSON.stringify(
    {
      refreshToken: "dGhpcyBpcyBhIHJlZnJl...",
    },
    null,
    2
  )}'`}
            language="bash"
          />
          <p className="text-sm text-muted-foreground">
            New access and refresh tokens are issued. The old refresh token is invalidated.
          </p>
        </CardContent>
      </Card>

      <Separator />

      <Card>
        <CardHeader>
          <CardTitle>Your Configuration</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <p className="text-xs font-medium text-muted-foreground mb-1">Access Token TTL</p>
              <Badge variant="outline">{docs.sessions.accessTokenTtlSeconds}s</Badge>
            </div>
            <div>
              <p className="text-xs font-medium text-muted-foreground mb-1">Refresh Token TTL</p>
              <Badge variant="outline">{docs.sessions.refreshTokenTtlSeconds}s</Badge>
            </div>
            <div>
              <p className="text-xs font-medium text-muted-foreground mb-1">Max Sessions</p>
              <Badge variant="outline">{docs.sessions.maxSessionsPerUser}</Badge>
            </div>
            <div>
              <p className="text-xs font-medium text-muted-foreground mb-1">Password Policy</p>
              <Badge variant="outline">
                {docs.auth.passwordMinLength}-{docs.auth.passwordMaxLength} chars
              </Badge>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
