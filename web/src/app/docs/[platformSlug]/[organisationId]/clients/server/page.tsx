"use client";

import { useDocs } from "@/components/docs/docs-provider";
import { useDocsApiBaseUrl } from "@/hooks/use-docs-api-url";
import { Endpoint } from "@/components/docs/endpoint";
import { CodeBlock } from "@/components/docs/code-block";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Info } from "lucide-react";

export default function ServerClientPage() {
  const docs = useDocs();
  const baseUrl = useDocsApiBaseUrl(docs.organisation.platformSlug);

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">SERVER Client</h1>
        <p className="mt-2 text-muted-foreground">
          Backend services and APIs that need full access to the organisation.
        </p>
      </div>

      <Alert>
        <Info className="h-4 w-4" />
        <AlertTitle>Full Access</AlertTitle>
        <AlertDescription>
          SERVER clients are always authenticated with a static token. They have
          full access to all organisation endpoints (equivalent to holding every
          permission).
        </AlertDescription>
      </Alert>

      <Card>
        <CardHeader>
          <CardTitle>Characteristics</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          <ul className="list-disc list-inside space-y-1 text-sm text-muted-foreground">
            <li>Always requires a static token</li>
            <li>Full access to all organisation endpoints</li>
            <li>Equivalent to holding every permission</li>
            <li>Scoped to its own organisation</li>
            <li>Use case: backend APIs, microservices, server-side apps</li>
          </ul>
        </CardContent>
      </Card>

      <Endpoint
        method="GET"
        path={`/organisations/${docs.organisation.id}/users`}
        description="List users (SERVER client)"
        auth="X-Client-Id + Authorization: Bearer <static_token>"
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
              metadata: {},
            },
          ],
          null,
          2
        )}
      />

      <Card>
        <CardHeader>
          <CardTitle>Authentication Headers</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground mb-4">
            SERVER clients must include both headers:
          </p>
          <CodeBlock
            code={`X-Client-Id: cli_your_client_key
Authorization: Bearer nx_your_static_token`}
            language="text"
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Example: Assign Role to User</CardTitle>
        </CardHeader>
        <CardContent>
          <CodeBlock
            code={`curl -X PATCH ${baseUrl}/organisations/${docs.organisation.id}/users/1 \\
  -H "Content-Type: application/json" \\
  -H "X-Client-Id: cli_your_client_key" \\
  -H "Authorization: Bearer nx_your_static_token" \\
  -d '${JSON.stringify(
    {
      roleIds: [1, 2],
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
