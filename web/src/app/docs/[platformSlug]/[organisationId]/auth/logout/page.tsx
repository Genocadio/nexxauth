"use client";

import { useDocs } from "@/components/docs/docs-provider";
import { Endpoint } from "@/components/docs/endpoint";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export default function LogoutPage() {
  const docs = useDocs();

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Logout</h1>
        <p className="mt-2 text-muted-foreground">
          Invalidate a refresh token and end the user session.
        </p>
      </div>

      <Endpoint
        method="POST"
        path={`/${docs.organisation.platformSlug}/auth/logout`}
        description="Logout and invalidate refresh token"
        auth="Public (with X-Client-Id header)"
        request={JSON.stringify(
          {
            refreshToken: "dGhpcyBpcyBhIHJlZnJl...",
          },
          null,
          2
        )}
      />

      <Card>
        <CardHeader>
          <CardTitle>Behavior</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          <ul className="list-disc list-inside space-y-1 text-sm text-muted-foreground">
            <li>Logout is idempotent — calling it multiple times with the same token is safe</li>
            <li>The refresh token is invalidated immediately</li>
            <li>All refresh tokens in the same family are revoked (if theft was detected)</li>
            <li>The access token remains valid until it expires</li>
            <li>After logout, the client should clear stored tokens</li>
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}
