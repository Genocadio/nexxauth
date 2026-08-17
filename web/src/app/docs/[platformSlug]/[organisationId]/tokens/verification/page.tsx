"use client";

import { useDocs } from "@/components/docs/docs-provider";
import { CodeBlock } from "@/components/docs/code-block";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Info } from "lucide-react";

export default function VerificationPage() {
  const docs = useDocs();

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Token Verification</h1>
        <p className="mt-2 text-muted-foreground">
          Verify JWT tokens using public keys.
        </p>
      </div>

      <Alert>
        <Info className="h-4 w-4" />
        <AlertTitle>RS256 Tokens</AlertTitle>
        <AlertDescription>
          Organisation tokens are signed with RS256 (RSA). Each organisation has
          its own key pair. Use the public key to verify tokens.
        </AlertDescription>
      </Alert>

      <Card>
        <CardHeader>
          <CardTitle>Fetch Public Keys</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-muted-foreground">
            Get the public keys for your organisation:
          </p>
          <CodeBlock
            code={`curl https://your-api-domain.com/${docs.organisation.platformSlug}/organisations/${docs.organisation.id}/keys`}
            language="bash"
          />
          <p className="text-sm text-muted-foreground">Response:</p>
          <CodeBlock
            code={JSON.stringify(
              [
                {
                  kid: "key-1",
                  publicKey: "-----BEGIN PUBLIC KEY-----\nMIIBIjANBg...",
                  active: true,
                },
              ],
              null,
              2
            )}
            language="json"
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Token Anatomy</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-muted-foreground">
            Organisation access tokens contain these claims:
          </p>
          <CodeBlock
            code={`{
  "sub": "1",                    // User ID
  "iss": "${docs.organisation.platformSlug}/${docs.organisation.id}",  // Issuer
  "iat": 1704067200,             // Issued at
  "exp": 1704068100,             // Expires at
  "kid": "key-1",               // Key ID for verification
  "roles": ["User"],            // Role names (not permissions)
  "org": ${docs.organisation.id}                    // Organisation ID
}`}
            language="json"
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Verify with OpenSSL</CardTitle>
        </CardHeader>
        <CardContent>
          <CodeBlock
            code={`# Extract the token payload
TOKEN="eyJhbGciOiJSUzI1NiIs..."
echo $TOKEN | cut -d'.' -f2 | base64 -d 2>/dev/null | jq .

# Verify with public key
echo $TOKEN | cut -d'.' -f1-2 > /tmp/token.txt
echo "-----BEGIN PUBLIC KEY-----" > /tmp/pubkey.pem
curl -s https://your-api-domain.com/${docs.organisation.platformSlug}/organisations/${docs.organisation.id}/keys | jq -r '.[0].publicKey' >> /tmp/pubkey.pem
echo "-----END PUBLIC KEY-----" >> /tmp/pubkey.pem
openssl dgst -sha256 -verify /tmp/pubkey.pem -signature <(echo -n "$TOKEN" | cut -d'.' -f3 | base64 -d) /tmp/token.txt`}
            language="bash"
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Verify with Node.js</CardTitle>
        </CardHeader>
        <CardContent>
          <CodeBlock
            code={`import jwt from 'jsonwebtoken';

const token = 'eyJhbGciOiJSUzI1NiIs...';
const publicKey = \`-----BEGIN PUBLIC KEY-----
\${yourPublicKey}
-----END PUBLIC KEY-----\`;

try {
  const decoded = jwt.verify(token, publicKey, {
    algorithms: ['RS256'],
    issuer: '${docs.organisation.platformSlug}/${docs.organisation.id}',
  });
  console.log('User ID:', decoded.sub);
  console.log('Roles:', decoded.roles);
} catch (err) {
  console.error('Token verification failed:', err.message);
}`}
            language="typescript"
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Verify with Python</CardTitle>
        </CardHeader>
        <CardContent>
          <CodeBlock
            code={`import jwt
import requests

token = "eyJhbGciOiJSUzI1NiIs..."
public_key = """-----BEGIN PUBLIC KEY-----
{your_public_key}
-----END PUBLIC KEY-----"""

try:
    decoded = jwt.decode(
        token,
        public_key,
        algorithms=["RS256"],
        issuer="${docs.organisation.platformSlug}/${docs.organisation.id}",
    )
    print("User ID:", decoded["sub"])
    print("Roles:", decoded["roles"])
except jwt.InvalidTokenError as e:
    print(f"Token verification failed: {e}")`}
            language="python"
          />
        </CardContent>
      </Card>
    </div>
  );
}
