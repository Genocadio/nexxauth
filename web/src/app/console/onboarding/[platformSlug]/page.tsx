"use client";

import { useEffect, useMemo, useState } from "react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  AtSign,
  Building2,
  Check,
  Fingerprint,
  KeyRound,
  Loader2,
  Lock,
  Mail,
  MessageSquare,
  Phone,
  ShieldCheck,
  Smartphone,
  Timer,
  User,
} from "lucide-react";
import { organisationsApi } from "@/api/organisations";
import { platformApi } from "@/api/platform";
import { FormField } from "@/components/shared/form-field";
import { SlugField } from "@/components/shared/slug-field";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { queryKeys } from "@/lib/query-keys";
import { getErrorMessage } from "@/types/errors";
import type { OrganisationResponse } from "@/types/api";
import type { ClientType, UserFieldType } from "@/types/enums";

/**
 * Setup wizard for an organisation that hasn't finished onboarding yet. It is
 * reached after registering a fresh platform (no org yet → step 1 creates
 * one), after creating an organisation (launched with ?org=), and is forced
 * by the console onboarding gate whenever any organisation is incomplete.
 * Progress is persisted on the organisation (`onboardingStep` 1..7,
 * 8 = complete), so the wizard resumes exactly where the user left off.
 */
export default function OnboardingPage() {
  const { platformSlug } = useParams<{ platformSlug: string }>();
  const router = useRouter();
  const qc = useQueryClient();
  const searchParams = useSearchParams();
  const orgParam = searchParams.get("org");

  const orgsQuery = useQuery({
    queryKey: ["onboarding-orgs", platformSlug],
    queryFn: () => organisationsApi.list(platformSlug),
    enabled: !!platformSlug,
  });

  const [advancedStep, setAdvancedStep] = useState<number | null>(null);
  const [created, setCreated] = useState<OrganisationResponse | null>(null);

  // The wizard targets the organisation it was launched for (?org=), falling
  // back to the first organisation that hasn't completed onboarding, then to
  // the first overall (fresh platform). A `created` org (step 1) always wins.
  const org = useMemo(() => {
    if (created) return created;
    const list = orgsQuery.data;
    if (!list || list.length === 0) return null;
    if (orgParam) {
      const match = list.find((o) => o.slug === orgParam);
      if (match) return match;
    }
    return list.find((o) => (o.onboardingStep ?? 0) < 8) ?? list[0];
  }, [created, orgsQuery.data, orgParam]);

  // Resume where the user left off: no org → step 1 (create); otherwise the
  // persisted onboardingStep (2 when the org was created outside the wizard).
  const step = advancedStep ?? (org ? (org.onboardingStep ?? 2) : 1);

  // Completed wizard (step 8) — and fully onboarded orgs — go to the console.
  useEffect(() => {
    if (org && step >= 8) {
      router.replace(`/console/organisations/${org.slug}`);
    }
  }, [org, step, router]);

  const advance = async (next: number, target: OrganisationResponse | null = org) => {
    setAdvancedStep(next);
    if (target) {
      try {
        await organisationsApi.update(platformSlug, target.slug, { onboardingStep: next });
        await qc.invalidateQueries({ queryKey: queryKeys.organisations });
      } catch (e) {
        // Progress is best-effort; the wizard state still advances locally.
        console.error(e);
      }
    }
  };

  const loading = orgsQuery.isLoading;

  return (
    <div className="mx-auto max-w-2xl space-y-8 py-10">
      <div className="space-y-2 text-center">
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10 text-primary">
          <ShieldCheck className="h-6 w-6" />
        </div>
        <h1 className="text-2xl font-semibold tracking-tight">
          {org ? `Set up ${org.name}` : "Set up your organisation"}
        </h1>
        <p className="text-sm text-muted-foreground">
          {org ? (
            <>
              Configuring{" "}
              <span className="font-medium text-foreground">{org.slug}</span> — you can change
              everything later from the console and pick up right here if you leave.
            </>
          ) : (
            <>
              You&apos;re configuring the identity system for your first organisation. You can
              change everything later from the console — and pick up right here if you leave.
            </>
          )}
        </p>
      </div>

      <StepRail current={step} />

      {loading ? (
        <div className="flex justify-center py-16">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </div>
      ) : step === 1 ? (
        <StepOrganisation
          platformSlug={platformSlug}
          onCreated={async (o) => {
            setCreated(o);
            await advance(2, o);
          }}
        />
      ) : step === 2 ? (
        <StepIdentifiers platformSlug={platformSlug} org={org!} advance={advance} />
      ) : step === 3 ? (
        <StepFields platformSlug={platformSlug} org={org!} advance={advance} />
      ) : step === 4 ? (
        <StepAuth platformSlug={platformSlug} org={org!} advance={advance} />
      ) : step === 5 ? (
        <StepSessions platformSlug={platformSlug} org={org!} advance={advance} />
      ) : step === 6 ? (
        <StepClient platformSlug={platformSlug} org={org!} advance={advance} />
      ) : (
        <StepKeys platformSlug={platformSlug} org={org!} advance={advance} />
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Progress rail
// ---------------------------------------------------------------------------

const STEPS = [
  { title: "Organisation", icon: Building2 },
  { title: "Identifiers", icon: AtSign },
  { title: "User fields", icon: User },
  { title: "Authentication", icon: Lock },
  { title: "Sessions", icon: Timer },
  { title: "Client", icon: Smartphone },
  { title: "Keys", icon: KeyRound },
];

function StepRail({ current }: { current: number }) {
  return (
    <ol className="flex items-center justify-center gap-1">
      {STEPS.map(({ title, icon: Icon }, i) => {
        const n = i + 1;
        const done = current > n;
        const active = current === n;
        return (
          <li key={title} className="flex items-center gap-1" title={title}>
            <span
              className={`flex size-8 items-center justify-center rounded-full border text-xs font-medium transition-colors ${
                done
                  ? "border-primary bg-primary text-primary-foreground"
                  : active
                    ? "border-primary bg-primary/10 text-primary"
                    : "border-input text-muted-foreground"
              }`}
            >
              {done ? <Check className="size-4" /> : <Icon className="size-4" />}
            </span>
            {n < STEPS.length ? (
              <span className={`h-px w-6 ${current > n ? "bg-primary" : "bg-border"}`} />
            ) : null}
          </li>
        );
      })}
    </ol>
  );
}

// ---------------------------------------------------------------------------
// Step 1 — organisation
// ---------------------------------------------------------------------------

function StepOrganisation({
  platformSlug,
  onCreated,
}: {
  platformSlug: string;
  onCreated: (org: OrganisationResponse) => Promise<void>;
}) {
  const [name, setName] = useState("");
  const [slug, setSlug] = useState("");
  const [description, setDescription] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setError("Organisation name is required");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const org = await organisationsApi.create(platformSlug, {
        name: name.trim(),
        slug: slug.trim() || undefined,
        description: description.trim() || undefined,
      });
      await onCreated(org);
    } catch (err) {
      setError(getErrorMessage(err));
      setBusy(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Building2 className="h-4 w-4 text-primary" /> Let&apos;s set up your organisation
        </CardTitle>
        <CardDescription>
          The first organisation becomes the tenant whose users you&apos;ll authenticate.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={submit} className="space-y-4">
          <FormField label="Organisation name" htmlFor="ob-name" error={error ?? undefined}>
            <Input
              id="ob-name"
              placeholder="Acme Inc."
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </FormField>
          <SlugField
            id="ob-slug"
            label="Organisation slug"
            name={name}
            value={slug}
            onChange={setSlug}
            type="ORGANISATION"
            platformSlug={platformSlug}
            placeholder="acme"
            hint="Lowercase letters, digits and hyphens. Derived from the name if omitted — it can never change."
          />
          <FormField label="Description (optional)" htmlFor="ob-desc">
            <Input
              id="ob-desc"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </FormField>
          {error ? (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>
          ) : null}
          <div className="flex justify-end">
            <Button type="submit" disabled={busy} className="gap-2">
              {busy ? <Loader2 className="animate-spin" /> : null}
              Continue
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Step 2 — sign-in identifiers
// ---------------------------------------------------------------------------

type IdentifierFlags = Pick<
  OrganisationResponse,
  "emailRequired" | "usernameRequired" | "phoneRequired" | "emailCanLogin" | "usernameCanLogin" | "phoneCanLogin"
>;

function StepIdentifiers({
  platformSlug,
  org,
  advance,
}: {
  platformSlug: string;
  org: OrganisationResponse;
  advance: (next: number, target?: OrganisationResponse | null) => Promise<void>;
}) {
  const [flags, setFlags] = useState<IdentifierFlags>({
    emailRequired: org.emailRequired,
    usernameRequired: org.usernameRequired,
    phoneRequired: org.phoneRequired,
    emailCanLogin: org.emailCanLogin,
    usernameCanLogin: org.usernameCanLogin,
    phoneCanLogin: org.phoneCanLogin,
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const rows: { key: "email" | "username" | "phone"; label: string; hint: string; icon: typeof Mail }[] = [
    { key: "email", label: "Email", hint: "Normalized and unique per organisation", icon: Mail },
    { key: "username", label: "Username", hint: "Lowercased and unique per organisation", icon: AtSign },
    { key: "phone", label: "Phone number", hint: "Normalized and unique per organisation", icon: Phone },
  ];

  const toggle = (flag: keyof IdentifierFlags, value: boolean) =>
    setFlags((f) => ({ ...f, [flag]: value }));

  const submit = async () => {
    if (!flags.emailCanLogin && !flags.usernameCanLogin && !flags.phoneCanLogin) {
      setError("At least one identifier must be usable for login");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await organisationsApi.update(platformSlug, org.slug, flags);
      await advance(3);
    } catch (err) {
      setError(getErrorMessage(err));
      setBusy(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <AtSign className="h-4 w-4 text-primary" /> How should your users sign in?
        </CardTitle>
        <CardDescription>
          Choose the identifiers your users can use. Each can be required on new users and/or
          usable for login — at least one must be usable for login.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {rows.map(({ key, label, hint, icon: Icon }) => (
          <div key={key} className="flex items-center justify-between gap-4 rounded-lg border p-3">
            <div className="flex items-center gap-3">
              <Icon className="h-4 w-4 text-muted-foreground" />
              <div>
                <Label className="text-sm font-medium">{label}</Label>
                <p className="text-xs text-muted-foreground">{hint}</p>
              </div>
            </div>
            <div className="flex items-center gap-4">
              <label className="flex items-center gap-2 text-sm">
                <Switch
                  checked={flags[`${key}Required`]}
                  onCheckedChange={(v) => toggle(`${key}Required` as keyof IdentifierFlags, v)}
                />
                Required
              </label>
              <label className="flex items-center gap-2 text-sm">
                <Switch
                  checked={flags[`${key}CanLogin`]}
                  onCheckedChange={(v) => toggle(`${key}CanLogin` as keyof IdentifierFlags, v)}
                />
                Can log in
              </label>
            </div>
          </div>
        ))}
        {error ? (
          <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>
        ) : null}
        <div className="flex justify-end pt-2">
          <Button onClick={submit} disabled={busy} className="gap-2">
            {busy ? <Loader2 className="animate-spin" /> : null}
            Continue
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Step 3 — user fields
// ---------------------------------------------------------------------------

function StepFields({
  platformSlug,
  org,
  advance,
}: {
  platformSlug: string;
  org: OrganisationResponse;
  advance: (next: number) => Promise<void>;
}) {
  const fieldsQuery = useQuery({
    queryKey: queryKeys.orgUserFields(org.slug),
    queryFn: () => organisationsApi.userFields(platformSlug, org.slug),
  });
  const [key, setKey] = useState("");
  const [fieldType, setFieldType] = useState<UserFieldType>("STRING");
  const [required, setRequired] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const systemFields: { label: string; required: boolean }[] = [
    { label: "Email", required: org.emailRequired },
    { label: "Username", required: org.usernameRequired },
    { label: "Phone number", required: org.phoneRequired },
  ];

  const addField = async () => {
    if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(key.trim())) {
      setError("Attribute name must be lowercase letters, digits and hyphens");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await organisationsApi.createUserField(platformSlug, org.slug, {
        key: key.trim(),
        fieldType,
        required,
      });
      setKey("");
      setFieldType("STRING");
      setRequired(false);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <User className="h-4 w-4 text-primary" /> What information should you store about users?
        </CardTitle>
        <CardDescription>
          Sign-in identifiers are already configured; add profile fields your users fill in.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="rounded-lg border p-3">
          <p className="mb-2 text-sm font-medium">Sign-in identifiers</p>
          <ul className="space-y-1.5">
            {systemFields.map((f) => (
              <li key={f.label} className="flex items-center justify-between text-sm">
                <span className="flex items-center gap-2">
                  <Check className="size-4 text-emerald-600 dark:text-emerald-400" />
                  {f.label}
                </span>
                <span className="text-xs text-muted-foreground">{f.required ? "Required" : "Optional"}</span>
              </li>
            ))}
          </ul>
        </div>

        <div className="rounded-lg border p-3">
          <p className="mb-2 text-sm font-medium">Custom fields</p>
          {fieldsQuery.data && fieldsQuery.data.length > 0 ? (
            <ul className="mb-3 space-y-1.5">
              {fieldsQuery.data.map((f) => (
                <li key={f.id} className="flex items-center justify-between text-sm">
                  <span className="font-mono">{f.key}</span>
                  <span className="text-xs text-muted-foreground">
                    {f.fieldType}
                    {f.required ? " · required" : ""}
                  </span>
                </li>
              ))}
            </ul>
          ) : null}
          <div className="grid gap-3 sm:grid-cols-2">
            <FormField label="Attribute name" htmlFor="of-key" hint="Lowercase letters, digits and hyphens">
              <Input
                id="of-key"
                placeholder="department"
                value={key}
                onChange={(e) => setKey(e.target.value)}
              />
            </FormField>
            <FormField label="Type" htmlFor="of-type">
              <Select value={fieldType} onValueChange={(v) => setFieldType(v as UserFieldType)}>
                <SelectTrigger id="of-type" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {(["STRING", "NUMBER", "BOOLEAN", "DATE", "EMAIL", "LINK"] as UserFieldType[]).map((t) => (
                    <SelectItem key={t} value={t}>
                      {t}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </FormField>
          </div>
          <div className="mt-3 flex items-center justify-between">
            <label className="flex items-center gap-2 text-sm">
              <Switch checked={required} onCheckedChange={setRequired} />
              Required — every user must provide a value
            </label>
            <Button type="button" variant="outline" onClick={addField} disabled={busy} className="gap-2">
              {busy ? <Loader2 className="animate-spin" /> : null}
              Add field
            </Button>
          </div>
          {error ? (
            <p className="mt-2 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>
          ) : null}
        </div>

        <div className="flex justify-end">
          <Button onClick={() => void advance(4)}>Continue</Button>
        </div>
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Step 4 — authentication methods
// ---------------------------------------------------------------------------

function StepAuth({
  platformSlug,
  org,
  advance,
}: {
  platformSlug: string;
  org: OrganisationResponse;
  advance: (next: number) => Promise<void>;
}) {
  const configQuery = useQuery({
    queryKey: queryKeys.orgAuthConfig(org.slug),
    queryFn: () => organisationsApi.authConfig(platformSlug, org.slug),
  });

  if (configQuery.isLoading) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }
  if (configQuery.isError || !configQuery.data) {
    return (
      <Card>
        <CardContent className="py-8 text-center text-sm text-destructive">
          Couldn&apos;t load the authentication settings. Please refresh and try again.
        </CardContent>
      </Card>
    );
  }
  return <StepAuthForm config={configQuery.data} platformSlug={platformSlug} org={org} advance={advance} />;
}

function StepAuthForm({
  config,
  platformSlug,
  org,
  advance,
}: {
  config: { passwordMinLength: number; passwordMaxLength: number; passwordExpirationDays: number; passwordHistoryCount: number };
  platformSlug: string;
  org: OrganisationResponse;
  advance: (next: number) => Promise<void>;
}) {
  const [min, setMin] = useState(config.passwordMinLength);
  const [max, setMax] = useState(config.passwordMaxLength);
  const [expiry, setExpiry] = useState(config.passwordExpirationDays);
  const [history, setHistory] = useState(config.passwordHistoryCount);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const futureMethods = [
    { icon: Fingerprint, label: "Passkey", note: "WebAuthn / FIDO2" },
    { icon: Mail, label: "Email OTP", note: "One-time code by email" },
    { icon: MessageSquare, label: "SMS OTP", note: "One-time code by text" },
    { icon: User, label: "Social login", note: "Google, GitHub, …" },
  ];

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await organisationsApi.updateAuthConfig(platformSlug, org.slug, {
        authType: "PASSWORD",
        passwordEnabled: true,
        passwordMinLength: min,
        passwordMaxLength: max,
        passwordExpirationDays: expiry,
        passwordHistoryCount: history,
      });
      await advance(5);
    } catch (err) {
      setError(getErrorMessage(err));
      setBusy(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Lock className="h-4 w-4 text-primary" /> How should users authenticate?
        </CardTitle>
        <CardDescription>
          Password authentication is enabled for this organisation. More methods are on the way.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex items-center justify-between rounded-lg border p-3">
          <div>
            <Label className="text-sm font-medium">Password</Label>
            <p className="text-xs text-muted-foreground">Enabled — users sign in with an identifier and password</p>
          </div>
          <Switch checked disabled aria-disabled />
        </div>

        <div className="grid gap-4 rounded-lg border p-3 sm:grid-cols-2">
          <FormField label="Minimum length" htmlFor="oa-min">
            <Input
              id="oa-min"
              type="number"
              min={1}
              max={72}
              value={min}
              onChange={(e) => setMin(Number(e.target.value))}
            />
          </FormField>
          <FormField label="Maximum length" htmlFor="oa-max">
            <Input
              id="oa-max"
              type="number"
              min={1}
              max={72}
              value={max}
              onChange={(e) => setMax(Number(e.target.value))}
            />
          </FormField>
          <FormField label="Expire after (days)" htmlFor="oa-expiry" hint="0 = passwords never expire">
            <Input
              id="oa-expiry"
              type="number"
              min={0}
              max={3650}
              value={expiry}
              onChange={(e) => setExpiry(Number(e.target.value))}
            />
          </FormField>
          <FormField label="Reuse history (count)" htmlFor="oa-history" hint="0 = reuse allowed">
            <Input
              id="oa-history"
              type="number"
              min={0}
              max={50}
              value={history}
              onChange={(e) => setHistory(Number(e.target.value))}
            />
          </FormField>
        </div>

        <div className="space-y-2">
          <p className="text-sm font-medium">Coming soon</p>
          {futureMethods.map(({ icon: Icon, label, note }) => (
            <div key={label} className="flex items-center justify-between rounded-lg border border-dashed p-3 opacity-60">
              <div className="flex items-center gap-3">
                <Icon className="h-4 w-4 text-muted-foreground" />
                <div>
                  <Label className="text-sm font-medium">{label}</Label>
                  <p className="text-xs text-muted-foreground">{note}</p>
                </div>
              </div>
              <span className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">Coming soon</span>
            </div>
          ))}
        </div>

        {error ? (
          <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>
        ) : null}
        <div className="flex justify-end">
          <Button onClick={submit} disabled={busy} className="gap-2">
            {busy ? <Loader2 className="animate-spin" /> : null}
            Continue
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Step 5 — sessions
// ---------------------------------------------------------------------------

function StepSessions({
  platformSlug,
  org,
  advance,
}: {
  platformSlug: string;
  org: OrganisationResponse;
  advance: (next: number) => Promise<void>;
}) {
  const settingsQuery = useQuery({
    queryKey: queryKeys.orgSessionSettings(org.slug),
    queryFn: () => organisationsApi.sessionSettings(platformSlug, org.slug),
  });

  if (settingsQuery.isLoading) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }
  if (settingsQuery.isError || !settingsQuery.data) {
    return (
      <Card>
        <CardContent className="py-8 text-center text-sm text-destructive">
          Couldn&apos;t load the session settings. Please refresh and try again.
        </CardContent>
      </Card>
    );
  }
  return (
    <StepSessionsForm settings={settingsQuery.data} platformSlug={platformSlug} org={org} advance={advance} />
  );
}

function StepSessionsForm({
  settings,
  platformSlug,
  org,
  advance,
}: {
  settings: { accessTokenTtlSeconds: number; refreshTokenTtlSeconds: number; maxSessionsPerUser: number };
  platformSlug: string;
  org: OrganisationResponse;
  advance: (next: number) => Promise<void>;
}) {
  const [access, setAccess] = useState(settings.accessTokenTtlSeconds);
  const [refresh, setRefresh] = useState(settings.refreshTokenTtlSeconds);
  const [sessions, setSessions] = useState(settings.maxSessionsPerUser);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await organisationsApi.updateSessionSettings(platformSlug, org.slug, {
        accessTokenTtlSeconds: access,
        refreshTokenTtlSeconds: refresh,
        maxSessionsPerUser: sessions,
      });
      await advance(6);
    } catch (err) {
      setError(getErrorMessage(err));
      setBusy(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Timer className="h-4 w-4 text-primary" /> Configure sessions
        </CardTitle>
        <CardDescription>Token lifetimes and how many concurrent sessions a user may hold.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <FormField label="Access token lifetime (seconds)" htmlFor="os-access" hint="Defaults to 900 (15 minutes)">
          <Input
            id="os-access"
            type="number"
            min={60}
            max={86400}
            value={access}
            onChange={(e) => setAccess(Number(e.target.value))}
          />
        </FormField>
        <FormField label="Refresh token lifetime (seconds)" htmlFor="os-refresh" hint="Defaults to 604800 (7 days)">
          <Input
            id="os-refresh"
            type="number"
            min={300}
            max={31536000}
            value={refresh}
            onChange={(e) => setRefresh(Number(e.target.value))}
          />
        </FormField>
        <FormField label="Max sessions per user" htmlFor="os-sessions" hint="Oldest sessions are evicted at the limit">
          <Input
            id="os-sessions"
            type="number"
            min={1}
            max={100}
            value={sessions}
            onChange={(e) => setSessions(Number(e.target.value))}
          />
        </FormField>
        {error ? (
          <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>
        ) : null}
        <div className="flex justify-end">
          <Button onClick={submit} disabled={busy} className="gap-2">
            {busy ? <Loader2 className="animate-spin" /> : null}
            Continue
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Step 6 — first client
// ---------------------------------------------------------------------------

function StepClient({
  platformSlug,
  org,
  advance,
}: {
  platformSlug: string;
  org: OrganisationResponse;
  advance: (next: number) => Promise<void>;
}) {
  const clientsQuery = useQuery({
    queryKey: queryKeys.orgClients(org.slug),
    queryFn: () => organisationsApi.clients(platformSlug, org.slug),
  });

  const [name, setName] = useState("");
  const [type, setType] = useState<ClientType>("WEB");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [createdToken, setCreatedToken] = useState<string | undefined>(undefined);
  const [createdKey, setCreatedKey] = useState<string | undefined>(undefined);

  const existing = clientsQuery.data?.filter((c) => c.clientKey === createdKey) ?? [];
  const client = existing[0] ?? clientsQuery.data?.[0];

  const submit = async () => {
    if (!name.trim()) {
      setError("Client name is required");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const created = await organisationsApi.createClient(platformSlug, org.slug, {
        name: name.trim(),
        type,
      });
      setCreatedToken(created.token);
      setCreatedKey(created.clientKey);
      await advance(7);
    } catch (err) {
      setError(getErrorMessage(err));
      setBusy(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Smartphone className="h-4 w-4 text-primary" /> Connect your first application
        </CardTitle>
        <CardDescription>
          A client is the app that talks to your API — your web app, mobile app or backend service.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <FormField label="Client name" htmlFor="oc-name">
          <Input id="oc-name" placeholder="My Web App" value={name} onChange={(e) => setName(e.target.value)} />
        </FormField>
        <FormField label="Client type" htmlFor="oc-type" hint="Web apps never need a token; server apps always do">
          <Select value={type} onValueChange={(v) => setType(v as ClientType)}>
            <SelectTrigger id="oc-type" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {(["WEB", "ANDROID", "IOS", "SERVER"] as ClientType[]).map((t) => (
                <SelectItem key={t} value={t}>
                  {t}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </FormField>
        {error ? (
          <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>
        ) : null}
        <div className="flex justify-end">
          <Button onClick={submit} disabled={busy} className="gap-2">
            {busy ? <Loader2 className="animate-spin" /> : null}
            Create client
          </Button>
        </div>

        {client ? (
          <div className="space-y-2 rounded-lg border p-3">
            <p className="text-sm font-medium">Your client is ready 🎉</p>
            <div className="space-y-1 font-mono text-xs">
              <p>
                <span className="text-muted-foreground">Client id:</span>{" "}
                <code className="break-all">{client.clientKey}</code>
              </p>
              {createdToken ? (
                <p>
                  <span className="text-muted-foreground">Client secret (shown once):</span>{" "}
                  <code className="break-all">{createdToken}</code>
                </p>
              ) : (
                <p className="text-muted-foreground">
                  The client secret was shown when the client was created — rotate the token from the
                  console if you lost it.
                </p>
              )}
            </div>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Step 7 — keys & integration
// ---------------------------------------------------------------------------

function StepKeys({
  platformSlug,
  org,
  advance,
}: {
  platformSlug: string;
  org: OrganisationResponse;
  advance: (next: number) => Promise<void>;
}) {
  const keysQuery = useQuery({
    queryKey: queryKeys.orgKeys(org.slug),
    queryFn: () => organisationsApi.keys(platformSlug, org.slug),
  });
  const clientsQuery = useQuery({
    queryKey: queryKeys.orgClients(org.slug),
    queryFn: () => organisationsApi.clients(platformSlug, org.slug),
  });
  const platformQuery = useQuery({
    queryKey: ["onboarding-platform", platformSlug],
    queryFn: () => platformApi.get(platformSlug),
  });

  // Guarded: this page is server-rendered (force-dynamic layout), where
  // `window` does not exist — the loading gate keeps StepKeys client-only, but
  // the fallback must not crash SSR if that changes.
  const base =
    platformQuery.data?.apiBaseUrl ??
    (typeof window !== "undefined" ? window.location.origin : "https://auth.example.com");
  const keysUrl = `${base}/organisations/${org.slug}/keys`;
  const client = clientsQuery.data?.[0];
  const activeKey = keysQuery.data?.find((k) => k.active);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <KeyRound className="h-4 w-4 text-primary" /> Your client is ready 🎉
        </CardTitle>
        <CardDescription>
          Here is everything your client needs to authenticate users. All values are available from
          the console at any time.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="rounded-lg border p-3">
          <p className="text-sm font-medium">Client id</p>
          <p className="font-mono text-xs break-all">{client?.clientKey ?? "—"}</p>
          <p className="mt-1 text-xs text-muted-foreground">
            Send it as the <code>X-Client-Id</code> header on every request.
          </p>
        </div>
        <div className="rounded-lg border p-3">
          <p className="text-sm font-medium">Issuer / API base</p>
          <p className="font-mono text-xs break-all">{base}</p>
          <p className="mt-1 text-xs text-muted-foreground">
            Login and register live at <code>{base}/auth/*</code>.
          </p>
        </div>
        <div className="rounded-lg border p-3">
          <p className="text-sm font-medium">Public keys (JWKS-style)</p>
          <p className="font-mono text-xs break-all">{keysUrl}</p>
          <p className="mt-1 text-xs text-muted-foreground">
            Public — any service can fetch the organisation&apos;s RSA public key to verify tokens offline.
          </p>
          {activeKey ? (
            <p className="mt-2 break-all font-mono text-[10px] text-muted-foreground">
              active kid={activeKey.kid} · {activeKey.publicKey.slice(0, 48)}…
            </p>
          ) : null}
        </div>

        <div className="flex justify-end pt-2">
          <Button onClick={() => void advance(8)}>Go to dashboard</Button>
        </div>
      </CardContent>
    </Card>
  );
}
