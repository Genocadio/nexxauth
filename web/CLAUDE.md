@AGENTS.md

# Organisation onboarding wizard

A fresh platform and every brand-new organisation go through a setup wizard at
`/console/onboarding/[platformSlug]` before the console is usable. It configures
an organisation end to end: identifiers, user fields, auth, sessions, first
client and signing keys.

## Progress model

- Progress is persisted **on the organisation** as `onboardingStep`:
  `1..7` = wizard step, `8` = complete, `null` = not started
  (`types/api.ts`, `types/requests.ts`; validated 1–8 in `lib/validation.ts`).
- The wizard resumes exactly where the user left off — step is derived from the
  org's persisted `onboardingStep` (defaults to 1 when no org exists yet,
  2 when the org was created outside the wizard).

## Entry points

| Trigger | Where | Behaviour |
|---|---|---|
| Fresh platform register | `hooks/use-auth.ts` `usePlatformRegister` | redirects to `/console/onboarding/{platformSlug}` (no org yet → step 1 creates one) |
| Creating an organisation | `components/organisations/organisation-dialog.tsx` | `router.push('/console/onboarding/{platformSlug}?org={slug}')` — resumes at step 2 |
| Active org is incomplete | `components/layout/onboarding-gate.tsx` | forced redirect to `.../onboarding/{platformSlug}?org={slug}` before the page renders |
| Any org incomplete | `app/console/organisations/page.tsx` | "Finish setting up your organisation" banner → wizard (no `?org=` → first incomplete org wins) |

## Onboarding gate

`OnboardingGate` (mounted in `app/console/layout.tsx`, inside
`RequirePlatformAuth`, wrapping the console shell) is **dedicated to the
organisation the user is currently working in** — it is never forced globally:

- The "working" org is the one in the URL (`/console/organisations/{slug}/...`).
  Platform views (organisation list, overview, profile, …) have no active org
  and are **never gated**.
- Only an org with `onboardingStep < 8` forces the wizard; the wizard route
  itself is exempt (it must not redirect to itself); completing step 8 clears
  the gate.
- A spinner is shown while the organisation list loads so the console never
  flashes before the redirect. On an API error the page renders normally (pages
  have their own error/retry state).

## Wizard steps (all in `app/console/onboarding/[platformSlug]/page.tsx`)

1. **Organisation** — create the org (name, slug via `SlugField`, description).
2. **Identifiers** — `*Required` / `*CanLogin` flags for email/username/phone;
   at least one identifier must be login-enabled.
3. **User fields** — shows the system identifiers, then adds custom fields
   (`key`, type `STRING|NUMBER|BOOLEAN|DATE|EMAIL|LINK`, `required`).
4. **Authentication** — password policy (min/max length, expiry days, history
   count) via `auth-config`; other methods are shown as "coming soon".
5. **Sessions** — access/refresh TTLs + `maxSessionsPerUser` via
   `session-settings`.
6. **Client** — create the first client (`WEB|ANDROID|IOS|SERVER`); the client
   id + secret (shown once) are displayed.
7. **Keys & integration** — client id, issuer/API base and the public
   `GET .../keys` URL; "Go to dashboard" → step 8.

Step 8 = complete → redirect to `/console/organisations/{org.slug}`.

## Behavioural details

- **Org targeting**: the wizard serves the org from `?org=`, falling back to the
  first org with `onboardingStep < 8`, then the first overall (fresh platform);
  a just-created org (step 1) always wins.
- **Progress persistence** (`advance(next)`): the step advances locally first,
  then the org's `onboardingStep` is PATCHed best-effort and organisation
  queries invalidated — a failed save must never block the wizard.
- **Org switcher** (`components/layout/org-switcher.tsx`): on the wizard route
  the "current" org comes from the `?org=` search param; everywhere else from
  the URL segments.
- **SSR guard** (Step 7): the page is server-rendered, so the API-base fallback
  checks `typeof window !== "undefined"` before using `window.location.origin`.

## Tests

`e2e/organisations.spec.ts` covers: creating an org launches its wizard at
step 2 (`?org=` in the URL), onboarding is dedicated to the working org (platform
views and fully-onboarded orgs are never gated), plus the existing
organisation CRUD flows.
