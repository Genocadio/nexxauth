# Nexxauth Console (frontend)

A modern admin console for the Nexxauth backend — manage platforms, organisations,
their users, roles, security policies and signing keys, plus a self-service portal
for organisation users.

## Stack

- **Next.js 16** (App Router, TypeScript)
- **Redux Toolkit** — platform console session state (localStorage-persisted)
- **TanStack Query** — all server state (queries + mutations with central
  invalidation)
- **Framer Motion** — page/section animations
- **shadcn/ui** + **Tailwind CSS 4** — design system, dark mode included
- **Zod** — form validation mirroring the backend's rules
- **Bun** — package manager and scripts

## Getting started

```bash
bun install
bun run dev          # http://localhost:3000
```

The backend must be running on `http://localhost:8080` (see the repo root
`README.md` — `docker compose up -d && ./gradlew bootRun`). Override the API base
with `NEXT_PUBLIC_API_BASE_URL` (see `.env.example`). Set `BACKEND_PUBLIC_URL` to
the backend's public origin to show the copyable API URL on the org portal
profile dashboard (the console dashboards get theirs from the backend).

```bash
bun run build        # production build (typechecks + lints)
bun run lint
```

## Testing

Two layers of browser tests, both against the real backend:

**`bun run test:e2e`** — the Playwright suite (`e2e/`): login, organisation
creation and organisation user management (create with roles + user fields,
validation, edit, disable, delete). A global setup registers a throwaway
platform once per run and seeds each test's session from it, so the suite never
hits the backend's rate limits. The backend and dev server are started
automatically (`playwright.config.ts` `webServer`) or reused if already running:

```bash
bun run test:e2e
```

**`bun run smoke`** — a single full-flow click-through (`scripts/clickthrough.mjs`)
that registers, manages an organisation and its users/roles/fields/keys, uses the
org portal, and fails on any console error, page error or failed request. It
requires the dev server on `:3000` and the backend on `:8080` already running:

```bash
bun run smoke
```

## Routes

| Route | Purpose |
|---|---|
| `/` | Landing page |
| `/register` | Create a platform (becomes its first super user) |
| `/login` | Platform sign in |
| `/console/overview` | Platform stats & details |
| `/console/users` | Platform user directory |
| `/console/organisations` | Organisation list |
| `/console/organisations/[organisationSlug]` | Org detail: users, roles, settings, fields, keys |
| `/console/profile` | Profile + change password |
| `/org` | Organisation portal entry |
| `/org/[platformSlug]/[organisationId]` | Org user sign in (server-rendered) |
| `/org/[platformSlug]/[organisationId]/profile` | Org user profile (server-rendered, behind middleware auth) |

## Structure

```
src/
  api/            thin typed API functions (one file per resource)
  app/            routes (App Router)
  components/
    ui/           shadcn components
    shared/       app-level reusable UI (headers, states, dialogs, badges)
    layout/       console shell, navigation, user menu
    auth/         auth page shell
    organisations/ users, users of orgs, roles, fields, keys tabs + dialogs
  hooks/          useForm, queries, mutations, auth mutations
  lib/            api client, endpoints, query keys, validation, constants
  store/          Redux store + auth slices with localStorage persistence
  types/          API/request types + enums mirroring the backend DTOs
```

## Conventions

- **Types**: `src/types/api.ts` and `src/types/requests.ts` mirror the backend
  records exactly — change them when the backend changes.
- **No duplicated queries**: every page uses the hooks in `src/hooks/queries.ts`;
  every mutation in `src/hooks/mutations.ts` invalidates exactly the keys it
  affects.
- **Auth**: the platform console and the org portal authenticate independently.
  The console keeps its session in Redux/localStorage; the org portal is
  **server-rendered behind Next middleware auth** — see below.

## Org portal auth (server-rendered, httpOnly cookies)

The org portal never exposes tokens to the browser. Session state lives in
`httpOnly` cookies and every protected render is server-side:

- **`src/proxy.ts`** — the Next 16 proxy (middleware) gates `/org/:path*/profile`:
  no session cookie → redirect to the portal login before the page renders.
- **Server actions** (`app/org/[platformSlug]/[organisationId]/actions.ts`) —
  `loginOrg` authenticates against the backend and sets the httpOnly session
  cookies; `logoutOrg` revokes the session and clears them.
- **`app/org/api/session/route.ts`** — the authoritative session check both
  portal pages call before rendering: verifies the access token, silently
  rotates tokens on a 401, and clears dead cookies.
- **`lib/org-auth.ts`** — server-only backend calls (login/refresh/logout/me)
  against `API_SERVER_URL`; never import it from client code.

This means the portal page and profile page are plain server components that
fetch the user server-side; there is no client-side `/users/me` call and no
Redux for org sessions.
