"use client";

import { useDocs } from "@/components/docs/docs-provider";
import { CodeBlock } from "@/components/docs/code-block";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";

export default function AnatomyPage() {
  const docs = useDocs();

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Token Anatomy</h1>
        <p className="mt-2 text-muted-foreground">
          Understanding JWT token structure and claims.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Access Token Structure</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-muted-foreground">
            Organisation access tokens are RS256-signed JWTs with the following structure:
          </p>
          <CodeBlock
            code={`// Header
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "key-1"
}

// Payload
{
  "sub": "1",                    // User ID (string)
  "iss": "${docs.organisation.platformSlug}/${docs.organisation.id}",  // Issuer
  "iat": 1704067200,             // Issued at (Unix timestamp)
  "exp": 1704068100,             // Expires at (Unix timestamp)
  "kid": "key-1",               // Key ID for verification
  "roles": ["User", "Admin"],   // Role names (NOT permissions)
  "org": ${docs.organisation.id}                    // Organisation ID
}`}
            language="json"
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Claims Reference</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            <div className="border rounded-lg p-3">
              <div className="flex items-center gap-2 mb-1">
                <code className="text-sm font-mono">sub</code>
                <span className="text-xs text-muted-foreground">— string</span>
              </div>
              <p className="text-sm text-muted-foreground">
                The user&apos;s ID. Always a string representation of the numeric ID.
              </p>
            </div>

            <div className="border rounded-lg p-3">
              <div className="flex items-center gap-2 mb-1">
                <code className="text-sm font-mono">iss</code>
                <span className="text-xs text-muted-foreground">— string</span>
              </div>
              <p className="text-sm text-muted-foreground">
                Issuer. Format: <code>{docs.organisation.platformSlug}/{docs.organisation.id}</code>
              </p>
            </div>

            <div className="border rounded-lg p-3">
              <div className="flex items-center gap-2 mb-1">
                <code className="text-sm font-mono">iat</code>
                <span className="text-xs text-muted-foreground">— number</span>
              </div>
              <p className="text-sm text-muted-foreground">
                Issued at. Unix timestamp when the token was created.
              </p>
            </div>

            <div className="border rounded-lg p-3">
              <div className="flex items-center gap-2 mb-1">
                <code className="text-sm font-mono">exp</code>
                <span className="text-xs text-muted-foreground">— number</span>
              </div>
              <p className="text-sm text-muted-foreground">
                Expires at. Unix timestamp when the token expires.
                Default: {docs.sessions.accessTokenTtlSeconds} seconds after issuance.
              </p>
            </div>

            <div className="border rounded-lg p-3">
              <div className="flex items-center gap-2 mb-1">
                <code className="text-sm font-mono">kid</code>
                <span className="text-xs text-muted-foreground">— string</span>
              </div>
              <p className="text-sm text-muted-foreground">
                Key ID. Identifies which public key to use for verification.
              </p>
            </div>

            <div className="border rounded-lg p-3">
              <div className="flex items-center gap-2 mb-1">
                <code className="text-sm font-mono">roles</code>
                <span className="text-xs text-muted-foreground">— string[]</span>
              </div>
              <p className="text-sm text-muted-foreground">
                Role names assigned to the user. <strong>Not permissions</strong> — permissions
                are resolved server-side from the database.
              </p>
            </div>

            <div className="border rounded-lg p-3">
              <div className="flex items-center gap-2 mb-1">
                <code className="text-sm font-mono">org</code>
                <span className="text-xs text-muted-foreground">— number</span>
              </div>
              <p className="text-sm text-muted-foreground">
                Organisation ID. The user belongs to this organisation.
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      <Separator />

      <Card>
        <CardHeader>
          <CardTitle>Refresh Token</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-muted-foreground">
            Refresh tokens are opaque strings (not JWTs). They are:
          </p>
          <ul className="list-disc list-inside space-y-1 text-sm text-muted-foreground">
            <li>Single-use — each refresh rotates the token</li>
            <li>Stored server-side (not in the JWT)</li>
            <li>Revoked on logout</li>
            <li>Subject to session limits ({docs.sessions.maxSessionsPerUser} per user)</li>
          </ul>
          <CodeBlock
            code={`{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJl..."
}`}
            language="json"
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Token Lifecycle</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-2 text-sm text-muted-foreground">
            <div className="flex items-center gap-2">
              <span className="font-mono text-xs">1.</span>
              <span>User registers or logs in</span>
              <span className="text-muted-foreground">→</span>
              <span>Access token + refresh token issued</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="font-mono text-xs">2.</span>
              <span>Client uses access token for API calls</span>
              <span className="text-muted-foreground">→</span>
              <span>Valid until expiration ({docs.sessions.accessTokenTtlSeconds}s)</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="font-mono text-xs">3.</span>
              <span>Access token expires</span>
              <span className="text-muted-foreground">→</span>
              <span>Client refreshes with refresh token</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="font-mono text-xs">4.</span>
              <span>Refresh token rotated</span>
              <span className="text-muted-foreground">→</span>
              <span>New access + refresh tokens issued</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="font-mono text-xs">5.</span>
              <span>User logs out</span>
              <span className="text-muted-foreground">→</span>
              <span>Refresh token revoked</span>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
