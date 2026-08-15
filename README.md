# Nexxauth

Multi-tenant authentication backend for the Nexxserve platform. A **platform** is a
tenant with its own **platform users** (email + password, `SUPER_USER` / `READ_ONLY`);
under each platform live **organisations**, which have their own RBAC, their own users,
their own password policy, and their own JWT signing keys. The two auth systems
(platform and organisation) are fully independent and never mix.

Production-grade structure: controller → service → repository layers, MapStruct
mappers, Flyway-versioned schema on PostgreSQL, JWT access + rotating refresh tokens
with reuse detection, per-IP rate limiting, a request-scoped audit trail, and a unified
error shape.

- [Domain model](#domain-model)
- [The two auth stacks](#the-two-auth-stacks)
- [Tech stack](#tech-stack)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [API reference](#api-reference)
- [Access control](#access-control)
- [Tokens](#tokens)
- [Security & hardening](#security--hardening)
- [Observability](#observability)
- [Testing](#testing)
- [Versioning](#versioning)
- [Flow documentation](#flow-documentation)

---

## Domain model

```
Platform (name, slug — slug immutable after creation)
 └─ PlatformUser (email + password auth, SUPER_USER | READ_ONLY)
     └─ Organisation (name, slug, description, useEmailAsUsername)
         ├─ OrganisationAuthConfig    (authType + password rules, one row per org)
         ├─ OrganisationSessionSettings (access/refresh TTLs + max sessions, one row per org)
         ├─ OrganisationRole          (name + Permission[]; permissions fixed in app)
         ├─ OrganisationUser          (identifier + optional password auth, roles)
         │    ├─ OrganisationPasswordHistory (last N password hashes)
         │    └─ OrganisationRefreshToken (rotating sessions; evicted when over the limit)
         └─ OrganisationSigningKey    (org's own RSA keypair; rotated on demand)
```

**Platform users** authenticate with email + password and hold platform power
(super user writes, everyone reads). **Organisation users** are managed data with
org-level authentication: they log in with the org's identifier (username, or email
when the org enables `useEmailAsUsername`) and receive access tokens signed by the
**organisation's own RSA key**. An org user can belong to several organisations (one
row per org) but never to none; org users are never platform users.

## The two auth stacks

| | Platform | Organisation |
|---|---|---|
| Endpoints | `/api/v1/auth/*` | `/api/v1/platforms/{slug}/auth/*` |
| Identifier | email (unique globally) | username or email (unique per org) |
| Access token | HS256 (shared secret) | RS256 (per-org keypair, `kid` in header) |
| Refresh token | rotating + reuse detection | rotating + reuse detection |
| Roles | `SUPER_USER` / `READ_ONLY` (platform-wide) | any number of org roles → permissions |
| Password policy | app-fixed (8–72) | per-org `auth-config` (length, expiry, history) |
| Session settings | app-fixed (15m / 7d) | per-org `session-settings` (TTLs, max sessions) |
| Rate limiting | own buckets | separate `org-*` buckets |

Platform tokens work on org routes as the admin path (members read, super users
write); org tokens are **rejected on platform routes** (401) and only ever apply
inside their own organisation.

## Tech stack

- **Java 21 / Spring Boot 4.1** — Web MVC, Security, Data JPA, Validation, Actuator, Flyway
- **PostgreSQL 16** via Docker Compose (H2 in PostgreSQL mode for tests)
- **JWT** — `jjwt` 0.12 (platform HS256; per-org RS256 with key rotation)
- **MapStruct 1.6** + Lombok — no hand-written DTO mapping
- **Bucket4j + Caffeine** — in-memory token-bucket rate limiting
- **Flyway** — versioned schema migrations (`src/main/resources/db/migration`, 7 migrations, 14 tables)

## Quick start

```bash
docker compose up -d          # PostgreSQL 16 on localhost:5432 (db/user/pass: nexxauth)
./gradlew bootRun             # app on :8080, actuator on :8081
```

Spring Boot's docker-compose integration starts the container and wires the
datasource automatically. Disable it with `SPRING_DOCKER_COMPOSE_ENABLED=false`
if you manage Postgres yourself.

**First request — create a platform (becomes its super user):**

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Ada","lastName":"Lovelace","email":"ada@nexx.io",
       "password":"sup3r-secret","platformName":"Analytical Engines"}'
```

The `platformSlug` is optional: omitted, it is derived from the platform name and
made unique if needed. It can never be changed afterwards.

**Then log in and use the access token:**

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ada@nexx.io","password":"sup3r-secret"}'
# → { accessToken, refreshToken, tokenType, expiresInSeconds, user }

curl http://localhost:8080/api/v1/platforms/analytical-engines \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

## Configuration

Everything lives in `src/main/resources/application.properties` (dev) and
`application-prod.properties` (activate with `SPRING_PROFILES_ACTIVE=prod`).
Overridable via environment variables.

| Setting | Default | Meaning |
|---|---|---|
| `JWT_SECRET` | dev secret (base64, 384-bit) | HS256 platform signing key — **must** be set in prod |
| `app.jwt.access-token-ttl` | `15m` | access token lifetime |
| `app.jwt.refresh-token-ttl` | `7d` | refresh token lifetime |
| `app.rate-limit.enabled` | `true` | master switch |
| `app.rate-limit.login.capacity` / `refill-per-minute` | `5` / `1` | platform + org login bucket |
| `app.rate-limit.register.capacity` / `refill-per-minute` | `3` / `1` | platform + org register bucket |
| `app.rate-limit.refresh.capacity` / `refill-per-minute` | `10` / `2` | platform + org refresh bucket |
| `app.rate-limit.use-forwarded-for` | `false` | set `true` only behind a trusted proxy that sets `X-Forwarded-For` |
| `app.rate-limit.store` | `in-memory` | bucket store: `in-memory` (single instance, Caffeine) or `redis` (shared store for horizontal scaling — see below) |
| `app.http.max-body-bytes` | `65536` | max request body size (larger → 413, DoS guard) |
| `management.server.port` | `8081` | actuator port (health/info/liveness/readiness) |
| `spring.jpa.hibernate.ddl-auto` | `validate` | schema is owned by Flyway; Hibernate only validates |

**Rate-limit store & scaling.** The default `in-memory` store (Caffeine) keeps each
instance's buckets local — correct for a single instance. To share buckets across
horizontally scaled instances, set `app.rate-limit.store=redis` and point it at the
Redis instance (`app.rate-limit.redis.host/port/password/ssl`); the repo's
`compose.yaml` includes a `redis` service for this. Only the Redis-backed store gives
correct per-IP throttling once more than one instance serves traffic.

**Production:** `SPRING_PROFILES_ACTIVE=prod` switches to ECS-structured JSON logging
(console + rotating file at `/var/log/nexxauth/nexxauth.log`, 50 MB × 14) and tones
down noisy frameworks. Every log line carries the `requestId` MDC value.

## API reference

All endpoints are under `/api/v1`. Errors always use the unified shape:

```json
{ "timestamp": "...", "status": 401, "error": "Unauthorized",
  "message": "Authentication required", "path": "/...", "requestId": "..." }
```

### Platform auth — `/api/v1/auth`

| Method & path | Auth | Description |
|---|---|---|
| `POST /auth/register` | public | creates a platform + its first `SUPER_USER`, returns tokens |
| `POST /auth/login` | public | email + password → tokens |
| `POST /auth/refresh` | public | rotates the refresh token, returns fresh tokens |
| `POST /auth/logout` | public | revokes the refresh token (idempotent, 204) |
| `GET /auth/me` | any platform user | own profile |
| `PATCH /auth/me` | any platform user | update own first/last name, phone |
| `POST /auth/me/password` | any platform user | change own password; revokes all sessions |

### Platform & users — `/api/v1/platforms`, `/api/v1/users`

| Method & path | Auth | Description |
|---|---|---|
| `GET /platforms/{slug}` | member | platform details + user count |
| `GET /platforms/{slug}/users` | member | platform user directory |
| `POST /platforms/{slug}/users` | super user | add a platform user (default role `READ_ONLY`) |
| `GET /users/{id}` | member | platform user by id |
| `PATCH /users/{id}` | super user | change role / enable / disable / profile |

### Organisations — `/api/v1/platforms/{slug}/organisations`

| Method & path | Auth | Description |
|---|---|---|
| `GET /organisations` | platform member | list organisations of the platform (org users → 403) |
| `POST /organisations` | platform super user | create an organisation |
| `GET /organisations/{organisationSlug}` | platform member, or the org user themselves | org details |
| `PATCH /organisations/{organisationSlug}` | platform super user | update name / description / settings |
| `DELETE /organisations/{organisationSlug}` | platform super user | delete org (users, roles, keys, tokens cascade) |

### Organisation auth — `/api/v1/platforms/{slug}/auth`

| Method & path | Auth | Description |
|---|---|---|
| `POST /auth/register` | public | creates an org user (password rules enforced), returns org tokens |
| `POST /auth/login` | public | `{organisationId, identifier, password}` → org tokens |
| `POST /auth/refresh` | public | rotate the org refresh token |
| `POST /auth/logout` | public | revoke the org refresh token (204) |

### Organisation RBAC — under `/api/v1/platforms/{slug}/organisations/{organisationSlug}`

| Method & path | Auth | Description |
|---|---|---|
| `GET /users` | member, or org user with `ORGANISATION_USER_READ` | org user directory |
| `GET /users/me` | org user of the org | own profile (no permission needed; platform users → 403) |
| `POST /users` | super user, or org user with `..._CREATE` | create user (`password` optional → no auth until set) |
| `GET /users/{userId}` | member, or org user with `..._READ` | one org user |
| `PATCH /users/{userId}` | super user, or org user with `..._UPDATE` | update profile / set or clear password / roles |
| `DELETE /users/{userId}` | super user, or org user with `..._DELETE` | delete org user |
| `GET /roles`, `POST /roles` | member / super user (writes) | list / create roles with permissions |
| `GET/PATCH/DELETE /roles/{roleId}` | member / super user (writes) | role CRUD |
| `GET /auth-config` | member, or org user | org auth settings + password rules |
| `PATCH /auth-config` | platform super user | change auth type + password rules |
| `GET /session-settings` | member, or org user | org session settings (token TTLs, session limit) |
| `PATCH /session-settings` | platform super user | change access/refresh TTLs + max sessions |
| `GET /keys` | **public** | organisation's public RSA keys (for other services to verify tokens) |
| `POST /keys/rotate` | platform super user | retire active key, generate a new one (old tokens keep verifying) |

### Actuator (management port 8081)

`GET /actuator/health` (+ `/liveness`, `/readiness`), `GET /actuator/info` — open,
no token, for monitoring probes.

## Access control

**Defense in depth:** URL rules in `SecurityConfig` + `@PreAuthorize` on methods +
service-level checks (`PlatformAccess`, `OrganisationAccess`) all enforce the same
policy. A request is authorized only if it passes every layer.

**Platform level** — platform tokens carry a role claim (`SUPER_USER` / `READ_ONLY`);
URL rules require the role for writes, membership is checked against the DB.

**Organisation level** — permissions are fixed in the app
(`ORGANISATION_USER_READ/CREATE/UPDATE/DELETE`); roles group them; users get roles,
never direct permissions. A role may have zero permissions. Enforcement matrix:

| Org endpoint | Platform member | Platform super user | Org user |
|---|---|---|---|
| `GET .../organisations` (list) | ✅ | ✅ | ❌ 403 |
| `GET .../organisations/{org}` | ✅ | ✅ | ✅ own org only; other org → 403 |
| `POST/PATCH/DELETE .../organisations` | ❌ | ✅ | ❌ |
| `GET .../users`, `.../users/{id}`, `.../roles` | ✅ | ✅ | needs `..._READ` |
| `POST .../users` | ❌ | ✅ | needs `..._CREATE` |
| `PATCH .../users/{id}` | ❌ | ✅ | needs `..._UPDATE` |
| `DELETE .../users/{id}` | ❌ | ✅ | needs `..._DELETE` |
| `GET .../users/me` | ❌ | ❌ | ✅ always |
| `GET .../auth-config`, `.../session-settings` | ✅ | ✅ | ✅ org user of the org |
| `PATCH .../auth-config`, `.../session-settings`, `POST .../keys/rotate` | ❌ | ✅ | ❌ |

## Tokens

**Access token.** Platform (15 min, fixed): HS256-signed with `sub` = user id, plus
`email`, `role`, `platformId`, `platformSlug`, `type: access`. Org: RS256-signed
with the **organisation's own keypair**, `kid` in the JOSE header, claims:

```json
{ "sub": "6", "iss": "nexxauth", "orgId": 3, "orgSlug": "rbac-corp",
  "roles": ["manager"], "type": "org-access", "iat": ..., "exp": ... }
```

Only **roles** are in the token — permissions are an internal nexxauth concept
(users never hold permissions directly, only via roles) and are **never exposed**
in the token. The roles are a **snapshot at issue time** (drift window = token
lifetime); every request re-validates user + org against the DB and resolves the
user's effective permissions from their roles server-side, so disabling a user
kills their tokens immediately and role changes take effect on the next request.

**Org token lifetimes are per-organisation** — the access TTL (default 15 min),
refresh TTL (default 7 days) and the concurrent-session limit come from the org's
`session-settings` row and are applied at issue time (register / login / refresh).

**Refresh token (rotating).** Opaque random value stored hashed (SHA-256) in the
DB; the platform lifetime is 7 days, the org lifetime comes from the org's
`session-settings`. Every refresh revokes the presented token and issues a new
one; presenting an already-rotated token is treated as **theft** — the entire
token family is revoked and an audit event is written. Expired/revoked tokens are
purged nightly (03:00). Password changes revoke every outstanding refresh token.
When an org user hits the org's `maxSessionsPerUser`, the oldest sessions are
**evicted** (invalidated quietly — an evicted token does *not* trip the theft
detector, so a stale device cannot kill the user's newer sessions).

## Security & hardening

- **BCrypt** password hashing everywhere (72-byte bound enforced).
- **Rate limiting** (per-IP token buckets, `Retry-After` header) on login, register
  and refresh — platform and org buckets are separate so one cannot exhaust the other.
- **Audit trail** — dedicated `AUDIT` logger emits structured events
  (`event= actor= organisation= ip=`) for register, login success/failure, refresh,
  logout, password change, token reuse (theft) and org key rotation; every event
  carries the request's `requestId`.
- **Unified errors** — every failure (validation, 401/403/404/405/409/413/415/429/500)
  returns the same JSON shape with the `requestId`; internal details never leak.
- **Body-size guard** — request bodies over `app.http.max-body-bytes` (default
  64 KB) are rejected with 413 before they are buffered, so an oversized JSON
  payload cannot be parsed into memory (DoS protection).
- **`X-Request-Id`** — accepted or generated, echoed on the response and placed in
  the MDC for log correlation.
- **Stateless API** — no server-side sessions, CSRF disabled, CORS not enabled.

## Observability

- `requestId` in every log line (dev console pattern) and every JSON event (prod ECS).
- Actuator health/liveness/readiness/info on a **separate management port** (8081),
  probes need no token. Bind to localhost or restrict via network policy in prod.
- Prod logging: one JSON document per line (ECS) to console and a rotating file —
  ready for Elastic/Loki/Datadog-style aggregators.

## Testing

- **100 unit/integration tests** — `./gradlew test` (H2 in PostgreSQL mode; Flyway
  migrations run on the test schema). Includes a **hardening suite** that proves
  every malformed input (broken JSON, wrong types, invalid enums, out-of-range
  values, path type mismatches, null-in-list, oversized bodies) yields a clean
  4xx — never a 500.
- **167-check live smoke suite** — `scripts/smoke-test.sh` exercises every flow
  with curl against a running instance and greps the app log for the audit trail:
  ```bash
  BASE=http://localhost:8080 MGMT=http://localhost:8081 \
    LOG=/tmp/nexxauth.log scripts/smoke-test.sh
  ```
  It expects a fresh app with `login.capacity=5` and `register.capacity=5`
  (e.g. `JAVA_TOOL_OPTIONS='-Dapp.rate-limit.login.capacity=5
  -Dapp.rate-limit.register.capacity=5'`).
- The full suite has been verified **end to end against real PostgreSQL 16**
  (fresh DB, all 7 Flyway migrations, 167/167 checks, plus restart-persistence
  checks). Note: two Postgres-only issues were caught this way — a lazy config
  insert inside a read-only transaction (fixed) and an org `auth-config` GET on an
  org without a config row; both had been tolerated by H2.

## Versioning

Semantic versioning (`MAJOR.MINOR.PATCH`), tracked in `build.gradle`:

- **MAJOR** — breaking API or behaviour changes (endpoint contract, token format,
  auth semantics). A new major requires migrating clients.
- **MINOR** — backwards-compatible features and additions.
- **PATCH** — backwards-compatible bug fixes.

Current: **1.0.0** (first release). Release versions are tagged in git
(`v1.0.0`, `v1.1.0`, …). Pre-release builds use a `-SNAPSHOT` suffix (e.g.
`1.1.0-SNAPSHOT`) while in development and drop it at release. The build version
flows into `/actuator/info` (`info.app.version`) and the prod ECS log service
version, so running instances report the exact build they came from.

## Flow documentation

Step-by-step walkthroughs of every flow — register, login, refresh, logout,
password change, token issuance, enforcement, key rotation, error handling, rate
limiting, auditing — are in **[docs/FLOWS.md](docs/FLOWS.md)**.
