"use client";

import { useDocs } from "@/components/docs/docs-provider";
import { Endpoint } from "@/components/docs/endpoint";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Info } from "lucide-react";

export default function RefreshPage() {
  const docs = useDocs();

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Refresh Token</h1>
        <p className="mt-2 text-muted-foreground">
          Exchange a refresh token for new access and refresh tokens.
        </p>
      </div>

      <Alert>
        <Info className="h-4 w-4" />
        <AlertTitle>Token Rotation</AlertTitle>
        <AlertDescription>
          Refresh tokens are single-use. Each refresh rotates the token. If a used
          refresh token is presented, all tokens in that family are revoked (theft detection).
        </AlertDescription>
      </Alert>

      <Endpoint
        method="POST"
        path={`/${docs.organisation.platformSlug}/auth/refresh`}
        description="Refresh access token"
        auth="Public (with X-Client-Id header)"
        request={JSON.stringify(
          {
            refreshToken: "dGhpcyBpcyBhIHJlZnJl...",
          },
          null,
          2
        )}
        response={JSON.stringify(
          {
            accessToken: "eyJhbGciOiJSUzI1NiIs...",
            refreshToken: "bmV3IHJlZnJlc2ggdG9r...",
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
              authType: "PASSWORD",
              roles: [{ id: 1, name: "User" }],
              createdAt: "2024-01-01T00:00:00Z",
              metadata: null,
            },
          },
          null,
          2
        )}
      />

      <Card>
        <CardHeader>
          <CardTitle>Best Practices</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          <ul className="list-disc list-inside space-y-1 text-sm text-muted-foreground">
            <li>Store refresh tokens securely (httpOnly cookie or secure storage)</li>
            <li>Never expose refresh tokens in client-side code</li>
            <li>Implement token refresh before expiration (e.g., when 80% of access token TTL has passed)</li>
            <li>Handle 401 errors by attempting a refresh, then redirect to login if refresh fails</li>
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}
