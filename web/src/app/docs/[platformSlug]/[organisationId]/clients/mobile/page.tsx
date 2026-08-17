"use client";

import { useDocs } from "@/components/docs/docs-provider";
import { CodeBlock } from "@/components/docs/code-block";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Info } from "lucide-react";

export default function MobileClientPage() {
  const docs = useDocs();

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">ANDROID / IOS Client</h1>
        <p className="mt-2 text-muted-foreground">
          Mobile applications for Android and iOS.
        </p>
      </div>

      <Alert>
        <Info className="h-4 w-4" />
        <AlertTitle>Configurable Authentication</AlertTitle>
        <AlertDescription>
          Mobile clients can be configured to require authentication or not.
          Without authentication, they behave like WEB clients (login/register only).
          With authentication, they have full API access like SERVER clients.
        </AlertDescription>
      </Alert>

      <Card>
        <CardHeader>
          <CardTitle>Characteristics</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          <ul className="list-disc list-inside space-y-1 text-sm text-muted-foreground">
            <li>Authentication is configurable (unlike WEB/SERVER)</li>
            <li>Without auth: login/register only (like WEB)</li>
            <li>With auth: full API access (like SERVER)</li>
            <li>When a valid user JWT is present, the user proceeds under their own roles</li>
            <li>Use case: mobile apps, progressive web apps</li>
          </ul>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Without Authentication</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground mb-4">
            Only login and register endpoints are accessible:
          </p>
          <CodeBlock
            code={`curl -X POST https://your-api-domain.com/${docs.organisation.platformSlug}/auth/login \\
  -H "Content-Type: application/json" \\
  -H "X-Client-Id: cli_your_client_key" \\
  -d '${JSON.stringify(
    {
      organisationId: docs.organisation.id,
      identifier: "johndoe",
      identifierType: "USERNAME",
      password: "securePassword123",
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
          <CardTitle>With Authentication</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground mb-4">
            Full API access with both headers:
          </p>
          <CodeBlock
            code={`curl -X GET https://your-api-domain.com/${docs.organisation.platformSlug}/organisations/${docs.organisation.id}/users \\
  -H "X-Client-Id: cli_your_client_key" \\
  -H "Authorization: Bearer nx_your_static_token"`}
            language="bash"
          />
        </CardContent>
      </Card>
    </div>
  );
}
