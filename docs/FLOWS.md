# Flow Documentation

Step-by-step walkthroughs of how the system actually works — the request pipeline,
every auth flow (platform and organisation), token issuance and enforcement, and the
cross-cutting concerns (rate limiting, errors, audit). All flows are verified live in
`scripts/smoke-test.sh` (186 checks) and in the integration tests (105 tests,
including a hardening suite proving malformed input never reaches a 500).

- [1. Request pipeline (every request)](#1-request-pipeline-every-request)
- [2. Platform register](#2-platform-register)
- [3. Platform login](#3-platform-login)
- [4. Platform refresh (rotation + theft detection)](#4-platform-refresh-rotation--theft-detection)
- [5. Platform logout & password change](#5-platform-logout--password-change)
- [6. Organisation register](#6-organisation-register)
- [7. Organisation login](#7-organisation-login)
- [8. Organisation refresh & logout](#8-organisation-refresh--logout)
- [9. Issuing a platform access token](#9-issuing-a-platform-access-token)
- [10. Issuing an organisation access token](#10-issuing-an-organisation-access-token)
- [11. Enforcing access on an authenticated request](#11-enforcing-access-on-an-authenticated-request)
- [12. Org key generation & rotation](#12-org-key-generation--rotation)
- [13. Password policy (org auth-config)](#13-password-policy-org-auth-config)
- [14. Session settings (org session-settings)](#14-session-settings-org-session-settings)
- [15. User fields (org user-fields)](#15-user-fields-org-user-fields)
- [16. Org user actions (temporary password & required fields)](#16-org-user-actions-temporary-password--required-fields)
- [17. Error handling](#17-error-handling)
- [18. Rate limiting](#18-rate-limiting)
- [19. Audit trail](#19-audit-trail)

---

## 1. Request pipeline (every request)

Every HTTP request crosses the same filter chain, in order:

```
HTTP request
  │
  ▼
RequestIdFilter         ① accepts/generates X-Request-Id, puts it in the MDC,
                        echoes it on the response
  │
  ▼
RateLimitFilter         ② only for POST /auth/login|register|refresh (platform
                        + org variants): consume a token from the per-IP bucket;
                        429 + Retry-After when empty
  │
  ▼
OrgJwtAuthenticationFilter   ③ only on /*/organisations/... :
                        parse org token (RS256, per-org key via kid), re-validate
                        org user against the DB, load roles + permissions
  │
  ▼
JwtAuthenticationFilter      ④ parse platform token (HS256), re-validate platform
                        user against the DB, load ROLE_*
  │
  ▼
SecurityConfig rules   ⑤ URL-level authorization (public auth paths, role rules)
  │
  ▼
Controller → @PreAuthorize   ⑥ method-level checks (org permissions)
  │
  ▼
Service → access checks      ⑦ transaction + membership/permission re-checks
  │
  ▼
Repository → PostgreSQL      ⑧ Flyway-owned schema, JPA
  │
  ▼
MapStruct → unified JSON response / unified ErrorResponse
```

Notes:

- The two JWT filters **never fight**: each skips if the security context is already
  populated, and each ignores tokens it cannot parse (wrong key type) — so an org
  token on a platform route simply stays unauthenticated and the entry point answers
  **401**, while a platform token on an org route authenticates as the admin path.
- The DB re-validation in steps ③/④ is what makes **disabling a user take effect
  immediately**, no matter what the token claims say.
- 401 vs 403: missing/invalid token → 401 (entry point); valid token but missing
  permission → 403 (access denied handler or service `ForbiddenException`).

## 2. Platform register

```
POST /api/v1/auth/register
{ firstName, lastName, email, password, phone?, platformName, platformSlug? }
```

1. **Rate limit** — `register` bucket (per IP) is consumed; empty → 429.
2. **Bean validation** — names/email/password required, email format, password
   8–72 chars (app-fixed rule), slug pattern `^[a-z0-9]+(-[a-z0-9]+)*$`.
3. **AuthService.register** (single transaction):
   - email is normalized (trim + lowercase); duplicate email → 409.
   - password is **BCrypt-encoded** — never stored in plain text.
   - `PlatformService.createPlatformWithOwner`:
     - slug provided → must be globally unique, else 409 (never renamed silently);
     - slug omitted → derived from the name (`Slugs.slugify`) and suffixed
       (`-2`, `-3`, …) until unique. The slug is **immutable** afterwards.
     - platform row is saved, the caller becomes its first `SUPER_USER`.
   - audit event `PLATFORM_REGISTER`.
4. **Tokens issued** (see §9) — 201 with
   `{ accessToken, refreshToken, tokenType, expiresInSeconds, user }`.

## 3. Platform login

```
POST /api/v1/auth/login
{ email, password }
```

1. **Rate limit** — `login` bucket consumed; empty → 429.
2. **AuthService.login** (write tx — a fresh refresh token is persisted):
   - email normalized; unknown email → audit `PLATFORM_LOGIN_FAILURE` + 401.
   - disabled account or wrong password → audit `PLATFORM_LOGIN_FAILURE`
     (and `PLATFORM_DISABLED` when disabled) + 401. Same error for both cases so
     the endpoint never reveals which one failed (no user enumeration).
   - success → audit `PLATFORM_LOGIN_SUCCESS`.
3. **Tokens issued** — access token (HS256) + a new opaque refresh token stored
   hashed in `refresh_tokens` (7-day TTL). Returns 200 with the same shape as
   register.

## 4. Platform refresh (rotation + theft detection)

```
POST /api/v1/auth/refresh
{ refreshToken }
```

1. **Rate limit** — `refresh` bucket (capacity 10, refill 2/min — higher than
   login because legit clients rotate on every 15-min access expiry).
2. **AuthService.refresh**:
   - `resolveSubject` → `AbstractRefreshTokenService.findUsable`:
     - token hash not found → 401 `Invalid refresh token`.
     - token **already revoked** (i.e. it was rotated before — someone is replaying
       an old token): revoke **every** token of the user (the whole token family) in
       its own `REQUIRES_NEW` transaction so the revocation survives the rollback of
       this rejected request, audit `PLATFORM_TOKEN_REUSE`, then 401.
     - token expired → 401.
   - `rotate` — mark the presented token revoked, issue a new one.
   - audit `PLATFORM_REFRESH`.
3. Returns a fresh access token + the **new** refresh token. The old refresh token
   is now dead; presenting it again triggers family revocation.

The same logic (`AbstractRefreshTokenService`) backs the org refresh flow — defined
once, subclassed for each entity.

**Org session limit** — before issuing a new org session (register/login),
`OrganisationRefreshTokenService.enforceSessionLimit` counts the user's live
sessions; at the org's `maxSessionsPerUser` the **oldest sessions are evicted**
(marked `evicted_at`, not `revoked_at`). Evicted tokens return 401 but — unlike
rotated-token replay — do **not** trigger family-wide theft detection, so a stale
evicted device cannot kill the user's newer sessions. The org's access/refresh
TTLs come from the same `session-settings` row and are applied at issue time.

## 5. Platform logout & password change

**Logout** — `POST /api/v1/auth/logout { refreshToken }` → 204, idempotent:
- audit attribution first (`findSubjectForAudit` — side-effect-free lookup, so a
  plain logout never trips the theft detector), then revoke the token.
- unknown/already-revoked token still returns 204.

**Change password** — `POST /api/v1/auth/me/password` (authenticated):
- verify `currentPassword` against the stored BCrypt hash → else 401.
- `revokeAllForUser` — **every outstanding refresh token is revoked**, forcing
  re-authentication on all devices.
- encode + persist the new password, audit `PLATFORM_PASSWORD_CHANGED`, 204.

## 6. Organisation register

```
POST /{slug}/auth/register
{ organisationId, identifier, password, firstName, lastName, metadata? }
```

1. **Rate limit** — `org-register` bucket (separate from the platform bucket).
2. **Bean validation** — all fields required, identifier ≤ 100, password ≤ 72
   (the detailed rules come from the org's auth-config, not the annotation).
3. **OrganisationAuthService.register**:
   - org must exist **under this platform** (id + platform match) → else 404.
   - `useEmailAsUsername` org → identifier must be a valid email, normalized, and
      unique per org → else 409; the user row stores `email`.
   - otherwise identifier is the `username`, normalized to **lowercase** (trim +
      `toLowerCase`, mirror of the email normalization) and unique per org → else 409 —
      so `Bob` and `bob` are the same account.
   - `authConfigService.setPassword` — the org's password rules are validated here
     (min/max length, 72-byte bound, reuse history) → violation is a 400; the hash
     is encoded, `passwordChangedAt` stamped, `authType` set to the org's configured
     type (`PASSWORD`).
   - user saved with **no roles**, audit `ORG_REGISTER`. An optional `metadata`
     map is validated and stored (see §15) — keys must be fields the org has
     defined.
4. **Org tokens issued** — access token signed with the org's **active RSA key**
   (created lazily if missing) + rotating refresh token. 201.

## 7. Organisation login

```
POST /{slug}/auth/login
{ organisationId, identifier, password }
```

1. **Rate limit** — `org-login` bucket.
2. **OrganisationAuthService.login** (write tx):
   - org under this platform → else 404.
   - identifier lookup (username or email depending on the org setting,
      **case-insensitive** for both) → if that misses, each **login-enabled user
      field** is tried in key order (see §15) → still unknown → audit
      `ORG_LOGIN_FAILURE` + 401.
   - **gate order** (all must pass, any failure = 401 + audit):
     1. `authType == PASSWORD` (a user with no auth configured can never log in),
     2. account enabled,
     3. a password hash exists,
     4. password matches,
     5. **not expired** — if the org's `passwordExpirationDays` has passed since
        `passwordChangedAt` → 401 `Password has expired` (forces a password reset;
        existing sessions drain naturally since expiry is only checked at login).
    - success → audit `ORG_LOGIN_SUCCESS`.
3. **Actions computed** — the pending [`OrgUserAction`](#16-org-user-actions-temporary-password--required-fields)
   list is appended to the response. When a **gating** action (CHANGE_PASSWORD) is
   pending the login returns **no refresh token** and a **fixed 5-minute access
   token** (see §16).
4. **Org tokens issued** (§10).

## 8. Organisation refresh & logout

Mirror of the platform flow, on the org endpoints and the org's refresh-token table
(`organisation_refresh_tokens`):

- `POST .../auth/refresh` — rotation + family revocation + `ORG_TOKEN_REUSE` audit.
  **Denied (401) while a gating action is pending**: the user must resolve the
  action and log in again before a session can be refreshed.
- `POST .../auth/logout` — audit first, then revoke; 204 idempotent.
- Both are rate-limited on their own `org-refresh` bucket.

## 9. Issuing a platform access token

`JwtService.generateAccessToken(user)` (HS256, shared `JWT_SECRET`):

```json
// header
{ "alg": "HS256" }
// payload
{ "sub": "12", "iss": "nexxauth", "email": "ada@nexx.io",
  "role": "SUPER_USER", "platformId": 1, "platformSlug": "analytical-engines",
  "type": "access", "iat": ..., "exp": ... }     // exp = iat + 15m
```

Issued together with a refresh token (opaque, stored hashed, 7d) — the client keeps
the refresh token to get new access tokens after expiry.

## 10. Issuing an organisation access token

`OrgJwtService.generateAccessToken(user, signingKey)` (RS256, the **org's own**
keypair):

```json
// header — unsigned JOSE, carries the key id
{ "kid": "2f1aa22c5e8f4f10", "alg": "RS256" }
// payload
{ "sub": "6", "iss": "nexxauth", "orgId": 3, "orgSlug": "rbac-corp",
  "roles": ["manager"], "type": "org-access", "iat": ..., "exp": ... }   // exp = iat + org access TTL (default 15m)
```

- The token carries **roles only** — permissions are an internal nexxauth concept
  (a user never holds a permission directly, only via roles) and are resolved
  server-side from the DB on every request, never placed in the token.
- The **15m lifetime comes from the org's `session-settings`** (default 900s, same
  as `app.jwt.access-token-ttl`); the org's refresh-token TTL and concurrent-session
  limit come from the same settings row. Each org can shorten/lengthen these via
  `PATCH .../session-settings`. **Exception:** while a gating action is pending the
  access token is fixed at **5 minutes regardless of the settings** (§16).
- `kid` lets any verifier pick the right public key from `GET .../keys` — including
  keys retired by rotation, so old tokens keep verifying until they expire.
- Verification (`OrgJwtService.parseAccessToken`) requires: key exists for the
  `kid`, RS256 signature valid, issuer matches, and `type == org-access` (a platform
  token can never pass here). The `kid` is read with jjwt's header parser (not a
  hand-rolled JSON scan) and resolved from a **Caffeine cache** keyed by `kid`
  (5 min; unknown kids are cached as misses for 1 min) so verification never hits
  the database and forged `kid`s cannot amplify into per-request lookups.

## 11. Enforcing access on an authenticated request

When a request carries a valid token, access is decided in three layers:

1. **URL rules** (`SecurityConfig`) — e.g. org paths require *some* authenticated
   token; platform writes require `ROLE_SUPER_USER`; actuator is open.
2. **Method rules** — `@PreAuthorize("hasRole('SUPER_USER')")` /
   `hasAuthority('ORG_USER')` / permission authorities (`PERM_...`).
3. **Service checks** — the authoritative membership tests:
   - `PlatformAccess.requireMember` — the token's `platformId` must be the platform
     in the path; `requireSuperUser` additionally demands the `SUPER_USER` role.
   - `OrganisationAccess` — platform users keep membership semantics (member reads,
     super-user writes); org users must belong to the **path's** organisation and
     hold the specific `Permission` for the operation. Exception: `users/me` and
     the org's own `GET /organisations/{org}` work for the org user **without any
     permission** ("every user can read himself / his own org").
4. **Action gating** (`OrgJwtAuthenticationFilter`) — while a **gating** action is
   pending the filter refuses to authenticate the user on anything but the action
   endpoints, so every other org endpoint answers **401**. Today the only gating
   action is CHANGE_PASSWORD; the only reachable endpoint while it is pending is
   `POST .../users/me/change-password` (§16).

Authorization is re-derived from the **database on every request** (the filter
re-loads the user, roles and permissions from the DB — the token's roles are just
a label; the DB is the source of truth), so permission changes are immediate.

## 12. Org key generation & rotation

- **Lazily created** on first token issuance (`activeKey`): a 2048-bit RSA keypair,
  PEM/DER-encoded, with a random 16-char `kid`, stored in `organisation_signing_keys`.
- **Rotation** (`POST .../keys/rotate`, platform super user):
  1. the org row is locked (PESSIMISTIC_WRITE) so two concurrent rotations cannot
     both insert an active key,
  2. current active key → `active=false` (retired, kept in the table),
  3. a fresh keypair is generated and becomes active,
  4. audit `ORG_KEY_ROTATED`.
- Tokens signed with the retired key **still verify** (their `kid` resolves to the
  archived key) until they expire; only *new* tokens use the new key. Clients that
  cache keys refresh from the public `GET .../keys` endpoint on `kid` misses.

## 13. Password policy (org auth-config)

`GET/PATCH .../organisations/{organisationSlug}/auth-config` — one row per org,
**created lazily with defaults** on first access (the row is persisted; the GET is a
write transaction on purpose — Postgres rejects inserts in read-only transactions).

| Setting | Default | Meaning |
|---|---|---|
| `authType` | `PASSWORD` | auth method new users get when a password is set (enum = extension point for OTP/SSO/…) |
| `passwordMinLength` / `passwordMaxLength` | `8` / `72` | length bounds (72 is also the bcrypt byte bound) |
| `passwordExpirationDays` | `0` | 0 = never; after N days login → 401 `Password has expired` |
| `passwordHistoryCount` | `0` | 0 = off; N = the last N passwords can't be reused |

Where each rule is enforced:

- **Setting a password** (org register, `POST .../users` with `password`,
  `PATCH .../users/{id}` with `password`): min/max length + byte bound + reuse
  history → violation = 400. The previous hash is pushed into
  `organisation_password_history` (trimmed to the configured depth). An **admin
  password change via `PATCH .../users/{id}`** additionally revokes every
  outstanding refresh token of that user — a reset forces re-authentication on
  all devices (mirror of the platform flow).
- **Temporary / forced change** — `POST .../users` with `temporaryPassword: true`
  (with a password) or `PATCH .../users/{id}` with `temporaryPassword: true`
  mark the password as **temporary** (`temporary_password` on the user): at next
  login the CHANGE_PASSWORD action is returned and the session is gated until
  the user changes the password via `POST .../users/me/change-password` (which
  clears the flag, revokes sessions and audits `ORG_PASSWORD_CHANGED`). Triggering
  the flag on an existing user revokes their sessions immediately (§16).
- **Clearing auth**: `PATCH .../users/{id}` with `"password": ""` → hash, authType
  and change-time are cleared; the user **cannot log in** until a new password is set.
- **Login**: authType must be `PASSWORD`, hash present, not disabled, not expired.

This config **never applies to platform auth** — the platform keeps its own fixed
8–72 rule.

## 14. Session settings (org session-settings)

`GET/PATCH .../organisations/{organisationSlug}/session-settings` — one row per
org, **created lazily with defaults** (same write-transaction caveat as the
auth-config: the GET persists the row). Defaults match the platform's `app.jwt.*`.

| Setting | Default | Meaning |
|---|---|---|
| `accessTokenTtlSeconds` | `900` (15m) | org access-token lifetime — signed into the token (`exp = iat + TTL`) and reported as `expiresInSeconds` in register/login/refresh responses |
| `refreshTokenTtlSeconds` | `604800` (7d) | org refresh-token lifetime — applied at issue **and** every rotation |
| `maxSessionsPerUser` | `5` | concurrent sessions per org user; overflow evicts the oldest |

Where each setting is enforced:

- **TTLs** — `OrganisationAuthService` reads the org's settings at issue time
  (`issueTokens` / `refresh`): the access token is signed with the org TTL and the
  refresh token is created/rotated with the org TTL. Changing the settings only
  affects tokens issued afterwards; existing tokens keep their original `exp`.
- **Session limit** — before issuing a new session (register/login, and on refresh
  after rotation) `OrganisationRefreshTokenService.enforceSessionLimit` counts the
  user's live sessions and, at the cap, **evicts the oldest** (marked `evicted_at`).
  An evicted token returns 401 but does **not** trigger the family-wide theft
  detection that a replayed rotated token does — a stale evicted device cannot
  kill the user's newer sessions. `maxSessionsPerUser = 1` ⇒ every new login
  signs out all previous devices.

Validation: access TTL ≥ 60s, refresh TTL ≥ 300s, sessions 1–100, and refresh TTL
must outlive access TTL (else 400). Access control mirrors `auth-config`: reads for
platform members + org users of the org, writes for platform super users only.
Never applies to platform auth (platform TTLs stay `app.jwt.*`).

## 15. User fields (org user-fields)

`GET/POST .../organisations/{organisationSlug}/user-fields`,
`PATCH/DELETE .../user-fields/{fieldId}` — org-defined extra attributes stored on
every user and returned under `metadata` on all user objects.

| Field | Meaning |
|---|---|
| `key` | machine name, unique per org (`^[a-z0-9]+(-[a-z0-9]+)*$`), immutable |
| `label` | human-readable name |
| `fieldType` | `STRING` / `NUMBER` / `BOOLEAN` / `DATE` — how values are validated and canonicalized |
| `loginEnabled` | when true, the field's value also works as a login identifier |
| `required` | when true, every user must have a value for this field; users missing it get the UPDATE_PROFILE action at login (§16) |

Values are stored as **canonical strings**: STRING trimmed, NUMBER
`BigDecimal.toPlainString()` so `1.50 == 1.5`, BOOLEAN `true|false`, DATE ISO
`yyyy-MM-dd`. On users the values appear as a `metadata` map `{key: value}`; the
`loginEnabled` flag is **config-level only** and is never echoed on the user
objects. Note that metadata is stored **in plaintext** in
`organisation_user_field_values` — do not use fields for secrets; encrypt
sensitive values in the client or add column-level encryption before putting
sensitive data here.

- **Access** — platform members read, platform super users write; org users need
  the matching `ORGANISATION_USER_FIELD_READ/CREATE/UPDATE/DELETE` role
  permission (reads mirror the user endpoints).
- **Setting values** — `POST/PATCH .../users` and `POST .../auth/register` accept
  an optional `metadata` map. Keys must be defined fields (else 400) and values
  are validated/normalized per the field's type (else 400). On PATCH only the
  keys present are touched; a null/blank value removes the key. Values of
  login-enabled fields must be unique per org (409), including after
  normalization. Value writes and field-config changes are serialized per
  organisation (pessimistic row lock), so the uniqueness rules cannot race.
- **Login** — when the identifier is neither the username nor the email, login
  tries each login-enabled field **in key order**, normalizing the identifier
  per the field's type (an unparseable identifier simply misses that field).
  STRING fields match **case-insensitively** (values differing only in case are
  duplicates), so `EMP123` and `emp123` identify the same user.
- **Toggling login** — enabling a field whose existing values are duplicated
  across users is rejected (409); disable login first, deduplicate, re-enable.
- **Type change** is rejected (400) while the field has values (they were stored
  under the old type) — clear the values first. **Deleting a field** removes all
  of its values and stops login-by-field.
- **Audit** — field create/update/delete are logged as
  `ORG_USER_FIELD_CREATED/UPDATED/DELETED` on the `AUDIT` logger.

## 16. Org user actions (temporary password & required fields)

Every org register/login/refresh response carries an **`actions` array** — the
pending things the user must resolve, in a stable order. It is computed by
`OrgUserActions` from the user's `temporary_password` flag and the values of the
org's **required** user fields, and is empty for a fully onboarded user. Each
action is either **gating** (restricts the session) or **advisory**:

| Action | Trigger | Gating? | Effect |
|---|---|---|---|
| `CHANGE_PASSWORD` | `temporary_password = true` | **yes** | access token fixed at **5 minutes**, **no refresh token**, only `POST .../users/me/change-password` reachable until resolved |
| `UPDATE_PROFILE` | a **required** user field has no value for this user | no | advisory — refresh + access tokens unaffected, all endpoints keep working |

**Temporary password flow** — a platform user registers an org user with
`POST .../users` and `temporaryPassword: true` (or triggers it later with
`PATCH .../users/{id}` `temporaryPassword: true`, which also **revokes the user's
sessions**):

1. First login returns `actions: ["CHANGE_PASSWORD"]`, `refreshToken: null`,
   `expiresInSeconds: 300` — the 5 minutes are a **fixed constant**, independent
   of the org's session settings.
2. `OrgJwtAuthenticationFilter` refuses to authenticate the user on anything but
   `POST .../users/me/change-password`, so all other org endpoints answer 401.
3. `POST .../users/me/change-password { currentPassword, newPassword }` (the user
   must be authenticated, i.e. hold the short-lived access token):
   - verifies `currentPassword` (401 on mismatch),
   - applies the org's password rules via `authConfigService.setPassword`,
   - clears `temporary_password`,
   - revokes every outstanding refresh token,
   - audits `ORG_PASSWORD_CHANGED`.
4. The next login issues a full session (`actions` empty, refresh token, org TTL).

**Required-field flow** — a field with `required: true` means every user must have
a value. A user missing one logs in with `actions: ["UPDATE_PROFILE"]` but gets the
normal access + refresh tokens (advisory only). They complete the action via
`PATCH .../users/me` (first/last name + `metadata`), after which the action
disappears. A user can hold both actions at once (e.g. a temporary password plus a
missing required field); the gating one still restricts the session.

## 17. Error handling

Two writers, one shape:

- **Inside the MVC layer** — `GlobalExceptionHandler` (`@RestControllerAdvice`)
  maps every exception to the right status:
  - 400 validation (bean validation → `fieldErrors` array, type mismatch, unreadable body)
  - 401/403 from `ApiException` subclasses and security exceptions
  - 404 (unknown routes, missing resources), 405, 406, 415
  - 409 (duplicate email/slug/identifier, data-integrity races)
  - 429 (rate limited)
  - 500 — unexpected exceptions are **logged with the requestId and stack trace**,
    the client only ever sees `"An unexpected error occurred"` (no internals leaked).
- **Outside the MVC layer** (security chain, rate-limit filter) —
  `ErrorResponseWriter` emits the identical JSON, always carrying the MDC `requestId`.

Every response — success or error — includes the `X-Request-Id` header, and every
error body includes the same id, so a failing request is traceable end to end.

## 18. Rate limiting

`RateLimitFilter` (runs before authentication, so brute force is throttled cheaply):

- **Token bucket per (endpoint, client IP)** — Bucket4j backed by Caffeine.
- Endpoints: `login`, `register`, `refresh` — for platform **and** org (`org-login`,
  `org-register`, `org-refresh` are separate buckets, so one auth system can never
  exhaust the other).
- Exhausted → **429** with a `Retry-After` header and the unified error body.
- Client IP: request remote address; enable `X-Forwarded-For` only behind a trusted
  proxy (`app.rate-limit.use-forwarded-for=true`).
- Defaults: login 5/min, register 3/min, refresh 10/2-per-min.

## 19. Audit trail

`AuthAuditService` writes to a dedicated `AUDIT` logger (INFO) — structured,
greppable, and correlated with `requestId` via the MDC:

```
AUDIT event=ORG_LOGIN_SUCCESS actor=jane organisation=oa-org ip=127.0.0.1
```

Events: `PLATFORM_*` and `ORG_*` variants of REGISTER, LOGIN_SUCCESS, LOGIN_FAILURE,
REFRESH, LOGOUT, PASSWORD_CHANGED, TOKEN_REUSE (theft — names the attacker's actor),
plus `ORG_KEY_ROTATED` and `PLATFORM_DISABLED`. In production (ECS structured
logging) each line is one JSON document ready for any log aggregator. The audit log
is intentionally side-effect-free — it can never fail or slow down the request path.
