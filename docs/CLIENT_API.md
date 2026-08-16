# Client & Token Integration Guide

How external apps talk to the Nexxauth organisation API, how a verifier that
holds the organisation's **public key** can validate a token and read roles,
and how to refresh tokens.

- [1. Concepts](#1-concepts)
- [2. Base URL & conventions](#2-base-url--conventions)
- [3. Client identity](#3-client-identity)
- [4. Organisation authentication](#4-organisation-authentication)
- [5. Accessing the organisation API](#5-accessing-the-organisation-api)
  - [5.1 Endpoint reference](#51-endpoint-reference)
  - [5.2 Default-deny without a client](#52-default-deny-without-a-client)
  - [5.3 CORS for browser apps](#53-cors-for-browser-apps)
- [6. Verifying organisation tokens (public key)](#6-verifying-organisation-tokens-public-key)
- [7. Refreshing tokens](#7-refreshing-tokens)
- [8. Platform (admin) tokens — brief](#8-platform-admin-tokens--brief)
- [9. HTTP statuses & error body](#9-http-statuses--error-body)
- [10. Rate limits](#10-rate-limits)

---

## 1. Concepts

Two distinct audiences talk to the API:

| Audience | Flow | Token type |
|---|---|---|
| **External app / end user** | Uses an **organisation** account | **RS256** JWT signed with the organisation's own RSA key — verifiable offline with a public key |
| **Console / admin** | Platform super-user or read-only member | **HS256** JWT signed with a shared `JWT_SECRET` — not verifiable offline |

This guide is about the organisation side: an app logs a user in through the
organisation auth endpoints, then calls the organisation API with the returned
access token. Any service that holds the organisation's public key can
independently verify that token and read the user's roles without contacting
Nexxauth again.

---

## 2. Base URL & conventions

Each platform lives at its own clean root origin. With platform slug `acme`:

```
https://auth.example.com/acme
```

All organisation-facing endpoints (auth, org API, keys) are served directly
under that origin — no `/api/v1` prefix. The platform-wide admin/console
endpoints (platform auth) are at `https://auth.example.com/auth/*`.

Every request uses JSON (`Content-Type: application/json`).

> **Finding your URL** — the dashboards show the platform URL (your **project
> base**) copyable, no `/api/v1` in sight (Supabase-style):
>
> - **Platform dashboard** (console → Overview) and the **organisation
>   dashboards** (console → organisation → Overview, and the org portal
>   profile) all show the same platform base:
>   `https://auth.example.com/acme`
>
> That is the project base URL — every other management endpoint lives under it
> (org auth at `.../acme/auth/*`, the org API at
> `.../acme/organisations/{organisationSlug}/*`, keys at
> `.../acme/organisations/{organisationSlug}/keys`).
>
> The value is derived from `BACKEND_PUBLIC_URL` plus the platform slug (the
> backend also returns it as `apiBaseUrl` in `GET /{slug}`). When neither the
> backend nor the web app knows the public origin, the dashboards omit the row —
> they never show a `/api/v1` URL.

Common headers:

| Header | When | Purpose |
|---|---|---|
| `X-Client-Id` | client calls (browser apps should always send it) | `cli_...` opaque key identifying your app — see [§3](#3-client-identity) |
| `Authorization: Bearer <jwt>` | org API calls after login | the access token (or the client's static token for server clients) |
| `Origin` | browser apps | echoed back by per-client CORS when trusted |
| `X-Request-Id` | optional | echoed on the response for correlating server logs |

All timestamps are ISO-8601. All `exp`/`iat` JWT claims are Unix seconds.

---

## 3. Client identity

An external app is registered inside an organisation as a **client**. Each
client has:

- **`clientKey`** — an opaque, non-guessable identifier (`cli_` + ~43 base64url
  chars) that the app sends as the **`X-Client-Id`** header. Shown in the
  console with a copy button.
- **A static token** — only for auth-required clients (`SERVER`, or
  `ANDROID`/`IOS` with authentication enabled). Format `nx_` + ~43 base64url
  chars, sent as `Authorization: Bearer <static token>`. Shown **once** on
  create/rotate; only its SHA-256 hash is stored.

Client types and their access rules:

| Type | Token required? | What it can reach |
|---|---|---|
| `WEB` | never | anonymous: only the org `login`/`register` endpoints. With a valid user JWT on the same request, the user proceeds under their own roles |
| `SERVER` | always | the **full** organisation API, scoped to the client's organisation |
| `ANDROID` / `IOS` | configurable | auth on → full org API; auth off → same as `WEB` |

A present `X-Client-Id` always wins over a bearer JWT for auth-required clients
(the request is the client's). Requests with an unknown or disabled client key
are rejected (`401 Unknown client` / `403 Client is disabled`).

---

## 4. Organisation authentication

Base path: `POST /{platformSlug}/auth/...`

These endpoints are public — a browser app calls them **with** `X-Client-Id` +
`Origin`; a server or script can call them without any headers.

> **Which organisation?** When the request carries an `X-Client-Id` header the
> organisation is identified through the client — the client's organisation is
> authoritative and **no `organisationId` is needed in the body** (any body id
> is ignored). Without a client header (server-side scripts, the admin console)
> the body's `organisationId` is **required**. The `organisationId` is a
> platform-internal id and never appears in the client-facing payloads below.

### 4.1 Register an organisation user

```http
POST /{platformSlug}/auth/register
X-Client-Id: cli_AbCdEfGhIjKlMnOpQrStUvWxYz1234567890_ab
Content-Type: application/json

{
  "username": "alice",
  "email": "alice@example.com",
  "phone": "+15551234567",
  "password": "correct horse battery staple",
  "firstName": "Alice",
  "lastName": "Example",
  "metadata": { "department": "engineering" }
}
```

Without `X-Client-Id` (server-side/platform-user flow) add
`"organisationId": 7` to the body.

`username`, `email` and `phone` are optional identifiers, unique per
organisation. Which ones are **required** on users and which can be used for
**login** is the organisation's sign-in identifier configuration (see the
console or `PATCH /{platformSlug}/organisations/{organisationSlug}`) — at
least one identifier must be usable for login, and a user registering must
provide one. `password` must satisfy the organisation's password policy (see
`auth-config`); when password authentication is disabled for the organisation
a password is not required.

`metadata` is optional. Its keys must be user fields the organisation has
defined (`GET .../user-fields`), and each value is validated/normalized per
that field's type (see §5) — unknown keys or invalid values are a 400.

### 4.2 Login

```http
POST /{platformSlug}/auth/login
X-Client-Id: cli_AbCdEfGhIjKlMnOpQrStUvWxYz1234567890_ab
Content-Type: application/json

{
  "identifier": "alice",
  "identifierType": "USERNAME",
  "authType": "PASSWORD",
  "password": "correct horse battery staple"
}
```

- `identifier` is the username, email or phone — whichever the organisation has
  enabled for login (plus any login-enabled user-field value).
- `identifierType` (`USERNAME` | `EMAIL` | `PHONE`) says what kind of
  identifier is being sent; when omitted the backend tries each enabled
  identifier in order.
- `authType` selects the authentication method and **defaults to `PASSWORD`**
  when omitted; today only `PASSWORD` exists, so `password` is always sent.
  Future methods (passkey, OTP, ...) will extend this field.
- Without `X-Client-Id` add `"organisationId": 7` to the body.

### 4.3 Response shape (register, login, refresh)

`200 OK` (register: `201 Created`) with an `OrgAuthResponse`:

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjNmMmE5YzFiN2UwZDRhNmYifQ.eyJzdWIiOiI0MiIsIm9yZ0lkIjo3LCJvcmdTbHVnIjoiYWNtZSIsInJvbGVzIjpbImFkbWluIiwic3VwcG9ydCJdLCJ0eXBlIjoib3JnLWFjY2VzcyIsImlzcyI6Im5leHhhdXRoIiwiaWF0IjoxNzMwMDAwMDAwLCJleHAiOjE3MzAwMDA5MDB9.c2lnbmF0dXJl",
  "refreshToken": "L3V9mqPZQ8uE4fD2hB7xG1kJwN5cR0aT6yM4pS8vX2wA",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "user": {
    "id": 42,
    "firstName": "Alice",
    "lastName": "Example",
    "username": "alice",
    "email": null,
    "phone": "+15551234567",
    "enabled": true,
    "temporaryPassword": false,
    "authType": "PASSWORD",
    "roles": [
      { "id": 3, "name": "admin" }
    ],
    "createdAt": "2026-01-01T00:00:00Z",
    "metadata": { "department": "engineering" }
  },
  "actions": []
}
```

| Field | Meaning |
|---|---|
| `accessToken` | the **RS256 JWT** to send as `Authorization: Bearer …` (validated in [§6](#6-verifying-organisation-tokens-public-key)) |
| `refreshToken` | **opaque** (not a JWT); single-use. `null` while a gating action is pending |
| `expiresInSeconds` | access token lifetime in seconds |
| `user.roles` | roles the user holds — **id + name only, never permissions** (permissions are resolved server-side on every request) |
| `actions` | pending org-user actions: `CHANGE_PASSWORD` (gating) and `UPDATE_PROFILE` (advisory) |

> **Gating actions.** When `CHANGE_PASSWORD` is pending (the account has a
> temporary password), `refreshToken` is `null`, the access token is capped at
> 5 minutes, and the only reachable org endpoint is
> `POST /users/me/change-password` until the user changes the password.

### 4.4 Logout

```http
POST /{platformSlug}/auth/logout
Content-Type: application/json

{ "refreshToken": "<opaque refresh token>" }
```

`204 No Content`. Idempotent; revokes that refresh token.

---

## 5. Accessing the organisation API

Authenticated org API base path:

```
/{platformSlug}/organisations/{organisationSlug}/...
```

Call it with the **access token**:

```http
GET /{platformSlug}/organisations/{organisationSlug}/users/me
X-Client-Id: cli_AbCdEfGhIjKlMnOpQrStUvWxYz1234567890_ab
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
```

Access is controlled server-side. Three actor kinds reach the org API, all
scoped to the organisation in the path:

| Actor | Authenticated as | Authority |
|---|---|---|
| **Org user** (browser app) | org-user JWT (`Authorization: Bearer …`) + `X-Client-Id` | their roles' permissions, resolved from the DB on every request |
| **Client** (server app) | static token (`Authorization: Bearer nx_…`) + `X-Client-Id` | `ORG_USER` + **every** organisation permission |
| **Platform user** (console) | platform JWT (HS256) | member reads; `SUPER_USER` writes |

### 5.1 Endpoint reference

All paths below are relative to `/{platformSlug}/organisations/{organisationSlug}`.
Reads marked *read* are open to platform members (`SUPER_USER`/`READ_ONLY`) and
org users holding the listed permission; writes are `SUPER_USER` (or the listed
org permission). The `/me` endpoints are self-service — any org user may call
them regardless of permissions.

**User responses never include permissions.** A user's `roles` come back as
`{ id, name }` only; a role's full definition (its `permissions`, and whether
it is `isDefault`) is only visible on the roles endpoints below.

| Method & path | Purpose | Access |
|---|---|---|
| `GET /organisations/{organisationSlug}` | read the org itself | org users of the org (no permission) |
| `PATCH /organisations/{organisationSlug}` | update identifier flags etc. | `SUPER_USER` |
| `GET /users` | list users | read · `PERM_ORGANISATION_USER_READ` |
| `POST /users` | create a user (optional `temporaryPassword`, `password`) | `SUPER_USER` · `PERM_ORGANISATION_USER_CREATE` |
| `GET /users/me` | own profile | any org user |
| `PATCH /users/me` | update own profile (first/last name, `metadata`) — completes `UPDATE_PROFILE` | any org user |
| `POST /users/me/change-password` | change own password — completes `CHANGE_PASSWORD` | any org user |
| `GET /users/{userId}` | read one user | read · `PERM_ORGANISATION_USER_READ` |
| `PATCH /users/{userId}` | update a user (roles, fields, password reset) | `SUPER_USER` · `PERM_ORGANISATION_USER_UPDATE` |
| `DELETE /users/{userId}` | delete a user | `SUPER_USER` · `PERM_ORGANISATION_USER_DELETE` |
| `GET /roles` | list roles (each carries its `permissions` + `isDefault`) | read · `PERM_ORGANISATION_USER_READ` |
| `POST /roles` | create a role (`name`, `permissions`, optional `isDefault` — default roles are auto-assigned to new users on register) | `SUPER_USER` |
| `GET /roles/{roleId}` | read one role | read · `PERM_ORGANISATION_USER_READ` |
| `PATCH /roles/{roleId}` | update a role (rename, replace permissions, toggle `isDefault`) | `SUPER_USER` |
| `DELETE /roles/{roleId}` | delete a role | `SUPER_USER` |
| `GET /user-fields` | list custom user fields | read · `PERM_ORGANISATION_USER_FIELD_READ` |
| `POST /user-fields` | define a field (`key`, `fieldType`, `loginEnabled`, `required`) | `SUPER_USER` · `PERM_ORGANISATION_USER_FIELD_CREATE` |
| `PATCH /user-fields/{fieldId}` | update a field | `SUPER_USER` · `PERM_ORGANISATION_USER_FIELD_UPDATE` |
| `DELETE /user-fields/{fieldId}` | delete a field (removes its values) | `SUPER_USER` · `PERM_ORGANISATION_USER_FIELD_DELETE` |
| `GET /auth-config` | password policy | read · any org user |
| `PATCH /auth-config` | update password policy | `SUPER_USER` |
| `GET /session-settings` | token TTLs + max sessions | read · any org user |
| `PATCH /session-settings` | update TTLs + session limit | `SUPER_USER` |
| `GET /clients` | list clients | read · `PERM_ORGANISATION_USER_READ` |
| `POST /clients` | create a client (response carries the `nx_` token once) | `SUPER_USER` |
| `GET /clients/{clientKey}` | read one client (`token` always `null` here) | read · `PERM_ORGANISATION_USER_READ` |
| `PATCH /clients/{clientKey}` | update a client (origins, auth, name) | `SUPER_USER` |
| `DELETE /clients/{clientKey}` | delete a client | `SUPER_USER` |
| `POST /clients/{clientKey}/rotate-token` | issue a new `nx_` token (shown once) | `SUPER_USER` |
| `GET /keys` | **public** — verification keys (`kid` + DER SPKI public key) | no auth |
| `POST /keys/rotate` | retire the active key, provision a new one | `SUPER_USER` |

### 5.2 Default-deny without a client

External browser access to an organisation's API **requires a client**: if a
request carries an org-user JWT and a foreign `Origin` but **no** `X-Client-Id`,
it is rejected with `403 Organisation access from a foreign origin requires a
client id`. The same-origin/server path passes through — the admin console (a
platform user) and server-side callers (no `Origin` header, e.g. curl) are
never blocked.

### 5.3 CORS for browser apps

A browser app must send both `Origin` and `X-Client-Id`, and the `Origin` must
be in that client's configured `allowedOrigins` (see the client in the console).
Only then are CORS headers echoed. Unknown `X-Client-Id` → `401 Unknown client`;
disabled client → `403 Client is disabled`; a no-auth client (e.g. `WEB`)
reaching anything beyond `login`/`register` without a user JWT → `403 This
client type can only access the organisation login and register endpoints`.

---

## 6. Verifying organisation tokens (public key)

Organisation tokens are **RS256** JWTs signed with the organisation's private
RSA-2048 key. The private key never leaves the server, but the public key is
served openly so any service can verify.

### 6.1 Fetch the public keys

```http
GET /{platformSlug}/organisations/{organisationSlug}/keys
```

Public — no authentication needed. Returns an array ordered oldest → newest:

```json
[
  {
    "kid": "3f2a9c1b7e0d4a6f",
    "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA…",
    "active": true
  },
  {
    "kid": "9d1e8f2a3b4c5d6e",
    "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA…",
    "active": false
  }
]
```

- `kid` — 16-hex key id; matches the JOSE `kid` header.
- `publicKey` — **standard Base64** of the **DER-encoded X.509
  SubjectPublicKeyInfo (SPKI)**. Wrap it in
  `-----BEGIN PUBLIC KEY-----` / `-----END PUBLIC KEY-----` to hand to most
  libraries.
- `active` — exactly one key is `true` (the signer). Retired keys stay listed so
  tokens signed before a rotation keep verifying until they expire.

**Verification rule:** always read the `kid` from the token's JOSE header, pick
that key from the list, and verify against it. Reject unknown `kid`s.

### 6.2 Token anatomy

Header:

```json
{ "alg": "RS256", "kid": "3f2a9c1b7e0d4a6f" }
```

Payload claims:

| Claim | Type | Meaning |
|---|---|---|
| `sub` | string | organisation user id |
| `orgId` | number | organisation id |
| `orgSlug` | string | organisation slug |
| `roles` | string[] | **role names** the user holds, e.g. `["admin","support"]` |
| `type` | string | always `org-access` |
| `iss` | string | always `nexxauth` |
| `iat` | number | issued-at (Unix seconds) |
| `exp` | number | expires-at (Unix seconds) |

> **Permissions are not in the token.** Roles are; permissions are resolved
> server-side from the database on every request and never shipped in the JWT.
> A verifier can authenticate the user and read their roles, but cannot derive
> permission-level authorization offline — that remains Nexxauth's job.

### 6.3 Verify (curl + openssl)

```bash
BASE=https://auth.example.com
PLATFORM={platformSlug}
ORG={organisationSlug}
TOKEN="<paste org access token>"

# 1. keys are public
curl -s "$BASE/$PLATFORM/organisations/$ORG/keys"

# 2. save the active key as a PEM file
curl -s "$BASE/$PLATFORM/organisations/$ORG/keys" \
  | jq -r '.[] | select(.active == true) | .publicKey' \
  | base64 -d > /tmp/pub.der
openssl pkey -inform DER -pubin -in /tmp/pub.der -out /tmp/pub.pem

# 3. split the JWT and verify the RS256 signature
decode_b64url() { # base64url -> raw bytes (any padding)
  python3 -c "import sys,base64;sys.stdout.buffer.write(base64.urlsafe_b64decode(sys.argv[1]+'='*(-len(sys.argv[1])%4)))" "$1"
}
H=$(printf '%s' "$TOKEN" | cut -d. -f1)
P=$(printf '%s' "$TOKEN" | cut -d. -f2)
S=$(printf '%s' "$TOKEN" | cut -d. -f3)
printf '%s.%s' "$H" "$P" > /tmp/token-data
decode_b64url "$S" > /tmp/token-sig

# signature check -> "Verified OK"
openssl dgst -sha256 -verify /tmp/pub.pem -signature /tmp/token-sig /tmp/token-data

# 4. inspect claims (roles, exp, ...)
decode_b64url "$P" | jq .
```

Always additionally check `exp` (and `iss == "nexxauth"`, `type == "org-access"`).

### 6.4 Verify (Node.js)

```js
const jwt = require("jsonwebtoken");

function spkiDerToPem(derB64) {
  const wrapped = derB64.match(/.{1,64}/g).join("\n");
  return `-----BEGIN PUBLIC KEY-----\n${wrapped}\n-----END PUBLIC KEY-----`;
}

async function verifyOrgToken(token, platformSlug, organisationSlug) {
  const header = JSON.parse(
    Buffer.from(token.split(".")[0], "base64url").toString("utf8")
  );

  const keys = await fetch(
    `https://auth.example.com/${platformSlug}/organisations/${organisationSlug}/keys`
  ).then((r) => r.json());

  const key = keys.find((k) => k.kid === header.kid);
  if (!key) throw new Error("Unknown key id");

  return jwt.verify(token, spkiDerToPem(key.publicKey), {
    algorithms: ["RS256"],
    issuer: "nexxauth",
  });
}

// => { sub, orgId, orgSlug, roles: ["admin"], type: "org-access", iat, exp }
```

### 6.5 Verify (Python)

```python
import base64
import json
import urllib.request

import jwt

def spki_der_to_pem(der_b64: str) -> str:
    b64 = base64.b64encode(base64.b64decode(der_b64)).decode()
    body = "\n".join(b64[i:i + 64] for i in range(0, len(b64), 64))
    return f"-----BEGIN PUBLIC KEY-----\n{body}\n-----END PUBLIC KEY-----"

def verify_org_token(token: str, platform_slug: str, organisation_slug: str) -> dict:
    header = json.loads(base64.urlsafe_b64decode(token.split(".")[0] + "=="))
    url = (f"https://auth.example.com/{platform_slug}/"
           f"organisations/{organisation_slug}/keys")
    with urllib.request.urlopen(url) as r:
        keys = json.load(r)
    key = next(k for k in keys if k["kid"] == header["kid"])
    return jwt.decode(token, spki_der_to_pem(key["publicKey"]),
                      algorithms=["RS256"], issuer="nexxauth")
```

### 6.6 Extracting roles

The decoded payload already carries the role names:

```json
{
  "sub": "42",
  "orgId": 7,
  "orgSlug": "acme",
  "roles": ["admin", "support"],
  "type": "org-access",
  "iss": "nexxauth",
  "iat": 1730000000,
  "exp": 1730000900
}
```

Use `roles` for coarse UI gating (e.g. "is the user an admin?"). For
permission-level checks, call the API and let Nexxauth enforce them.

---

## 7. Refreshing tokens

Refresh tokens are **opaque single-use** strings (≈43 base64url chars), **not
JWTs** — never decode them; treat them as passwords. Store them securely (HTTPonly
cookie or secure storage) and use them to get fresh access tokens.

### 7.1 Refresh

```http
POST /{platformSlug}/auth/refresh
Content-Type: application/json

{ "refreshToken": "<opaque refresh token from login or a previous refresh>" }
```

Response: a fresh `OrgAuthResponse` — new `accessToken` **and** a new
`refreshToken`. **The presented refresh token is rotated: it becomes invalid the
moment it is used, so only the returned one is valid.** TTLs come from the
organisation's session settings (defaults: access 900 s, refresh 604800 s).

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6Ii4uLiJ9.eyJzdWIiOiI0MiIsInJvbGVzIjpbImFkbWluIl0sInR5cGUiOiJvcmctYWNjZXNzIiwiaXNzIjoibmV4eGF1dGgiLCJpYXQiOjE3MzAwMDA5MDAsImV4cCI6MTczMDAwMTgwMH0..",
  "refreshToken": "X2kLpQ9cV4mB7nZ1sD8wR3tY6uA0eF5gH2jK9lM4pS8xW",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "user": { "...": "..." },
  "actions": []
}
```

### 7.2 Replay protection (important)

Because every refresh rotates the token, presenting an **already-used** refresh
token is treated as token theft: Nexxauth revokes **every** outstanding refresh
token of that user (all their sessions) and rejects the request. So:

- Never reuse a refresh token.
- If you get an error after a refresh, stop the refresh loop and force a new
  login — the session family was revoked.
- On login after a gating action, remember `refreshToken` can be `null` (see
  [§4.3](#43-response-shape-register-login-refresh)).

### 7.3 Logout

`POST .../auth/logout` with the current refresh token revokes it (`204`).

---

## 8. Platform (admin) tokens — brief

Console/admin tokens come from `POST /auth/login` (or `/register`):

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.…",
  "refreshToken": "<opaque>",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "user": {
    "role": "SUPER_USER",
    "platform": { "id": 1, "name": "Acme", "slug": "acme" },
    "...": "..."
  }
}
```

These are **HS256** tokens signed with the shared `JWT_SECRET` — **not
verifiable with any public key**; only Nexxauth itself (or a party holding the
secret) can verify them. They carry a single `role` claim
(`SUPER_USER`/`READ_ONLY`) and are meant for the console. Endpoints:
`POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`.

---

## 9. HTTP statuses & error body

| Status | Meaning |
|---|---|
| `200` / `201` / `204` | OK / created / no content |
| `400` | malformed body, wrong credentials, or a validation failure (`fieldErrors`) |
| `401` | unknown client, invalid/expired token, invalid client token |
| `403` | disabled client, foreign origin without a client id, client type not allowed here |
| `404` | unknown platform/org/client/key |
| `409` | duplicate email/slug/identifier, data-integrity race |
| `429` | rate limited (auth endpoints) — see [§10](#10-rate-limits) |

Every error carries one body:

```json
{
  "timestamp": "2026-08-16T09:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid client token",
  "path": "/acme/auth/login",
  "requestId": "7f3a…",
  "fieldErrors": null
}
```

`requestId` lets you reference a request in the server logs. Validation failures
populate `fieldErrors` as `[{ "field": "password", "message": "…" }]`.

---

## 10. Rate limits

Per-client-IP token-bucket limits on the public auth endpoints:

| Endpoint | Capacity | Refill |
|---|---|---|
| login | 5 | 1 / min |
| register | 3 | 1 / min |
| refresh | 10 | 2 / min |