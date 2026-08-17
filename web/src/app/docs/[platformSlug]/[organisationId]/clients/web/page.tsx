"use client";

import { useDocs } from "@/components/docs/docs-provider";
import { Endpoint } from "@/components/docs/endpoint";
import { CodeBlock } from "@/components/docs/code-block";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { AlertTriangle } from "lucide-react";

export default function WebClientPage() {
  const docs = useDocs();

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">WEB Client</h1>
        <p className="mt-2 text-muted-foreground">
          Browser-based single-page applications (SPAs).
        </p>
      </div>

      <Alert>
        <AlertTriangle className="h-4 w-4" />
        <AlertTitle>No Static Token</AlertTitle>
        <AlertDescription>
          WEB clients are never authenticated with a static token. They can only
          access the login and register endpoints. To access other endpoints, the
          user must be logged in and the request must include their JWT.
        </AlertDescription>
      </Alert>

      <Card>
        <CardHeader>
          <CardTitle>Characteristics</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          <ul className="list-disc list-inside space-y-1 text-sm text-muted-foreground">
            <li>Never requires a static token</li>
            <li>Only accessible endpoints: login, register</li>
            <li>CORS origins are enforced</li>
            <li>When a valid user JWT is present, the user proceeds under their own roles</li>
            <li>Use case: web dashboards, admin panels</li>
          </ul>
        </CardContent>
      </Card>

      <Endpoint
        method="POST"
        path={`/${docs.organisation.platformSlug}/auth/login`}
        description="Login (WEB client)"
        auth="X-Client-Id header only (no static token)"
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
      />

      <Card>
        <CardHeader>
          <CardTitle>CORS Configuration</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground mb-4">
            WEB clients enforce CORS. Configure allowed origins when creating the client:
          </p>
          <CodeBlock
            code={`{
  "name": "My Web App",
  "type": "WEB",
  "allowedOrigins": ["https://app.example.com", "https://staging.example.com"]
}`}
            language="json"
          />
        </CardContent>
      </Card>
    </div>
  );
}
