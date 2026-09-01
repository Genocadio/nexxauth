"use client";

import { useEffect, useState } from "react";
import { KeyRound, Loader2, Mail, Timer, Trash2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { ErrorState } from "@/components/shared/error-state";
import { FormField } from "@/components/shared/form-field";
import { TableSkeleton } from "@/components/shared/loading";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useDeleteOrganisation, useUpdateOrgAuthConfig, useUpdateOrganisation, useUpdateOrgSessionSettings } from "@/hooks/mutations";
import { useOrganisation, useOrgAuthConfig, useOrgSessionSettings } from "@/hooks/queries";
import { useForm } from "@/hooks/use-form";
import { authConfigSchema, sessionSettingsSchema } from "@/lib/validation";
import { z } from "zod";
import { DURATION_UNITS, decomposeDuration, formatDuration, toSeconds, type DurationUnit } from "@/lib/constants";

interface OrgSettingsTabProps {
  platformSlug: string;
  organisationId: number;
}

export function OrgSettingsTab({ platformSlug, organisationId }: OrgSettingsTabProps) {
  const [section, setSection] = useState("auth");

  return (
    <Tabs value={section} onValueChange={setSection}>
      <TabsList>
        <TabsTrigger value="auth">Auth</TabsTrigger>
        <TabsTrigger value="session">Session</TabsTrigger>
        <TabsTrigger value="misc">Miscellaneous</TabsTrigger>
      </TabsList>

      <TabsContent value="auth" className="mt-6 space-y-6">
        <IdentifiersCard platformSlug={platformSlug} organisationId={organisationId} />
        <AuthConfigCard platformSlug={platformSlug} organisationId={organisationId} />
      </TabsContent>

      <TabsContent value="session" className="mt-6">
        <SessionSettingsCard platformSlug={platformSlug} organisationId={organisationId} />
      </TabsContent>

      <TabsContent value="misc" className="mt-6">
        <DangerZoneCard platformSlug={platformSlug} organisationId={organisationId} />
      </TabsContent>
    </Tabs>
  );
}

// ---------------------------------------------------------------------------
// Auth: email-as-username
// ---------------------------------------------------------------------------

function IdentifiersCard({ platformSlug, organisationId }: OrgSettingsTabProps) {
  const org = useOrganisation(organisationId);
  const update = useUpdateOrganisation(platformSlug, organisationId);

  if (org.isLoading) return <TableSkeleton rows={3} columns={3} />;
  if (org.isError || !org.data) return <ErrorState error={org.error ?? new Error("Organisation not found")} onRetry={() => org.refetch()} />;

  const data = org.data;

  /** Toggle one flag; the full set is sent so the at-least-one-login rule
   * (enforced server-side too) never leaves the UI out of sync. */
  const setFlag = (key: "emailRequired" | "usernameRequired" | "phoneRequired" | "emailCanLogin" | "usernameCanLogin" | "phoneCanLogin", value: boolean) => {
    update
      .mutateAsync({
        emailRequired: key === "emailRequired" ? value : data.emailRequired,
        usernameRequired: key === "usernameRequired" ? value : data.usernameRequired,
        phoneRequired: key === "phoneRequired" ? value : data.phoneRequired,
        emailCanLogin: key === "emailCanLogin" ? value : data.emailCanLogin,
        usernameCanLogin: key === "usernameCanLogin" ? value : data.usernameCanLogin,
        phoneCanLogin: key === "phoneCanLogin" ? value : data.phoneCanLogin,
      })
      .catch(() => undefined);
  };

  const identifiers: { key: "email" | "username" | "phone"; label: string; hint: string }[] = [
    { key: "email", label: "Email", hint: "Normalized and unique per organisation" },
    { key: "username", label: "Username", hint: "Lowercased and unique per organisation" },
    { key: "phone", label: "Phone number", hint: "Normalized and unique per organisation" },
  ];

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Mail className="h-4 w-4 text-primary" /> Sign-in identifiers
        </CardTitle>
        <CardDescription>
          Which identifiers your users sign in with. Each can be required on new users and/or
          usable for login — at least one must be usable for login.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {identifiers.map(({ key, label, hint }) => (
          <div key={key} className="flex items-center justify-between gap-4 rounded-lg border p-3">
            <div>
              <Label className="text-sm font-medium">{label}</Label>
              <p className="text-xs text-muted-foreground">{hint}</p>
            </div>
            <div className="flex items-center gap-4">
              <label className="flex items-center gap-2 text-sm">
                <Switch
                  checked={data[`${key}Required`]}
                  disabled={update.isPending}
                  onCheckedChange={(checked) => setFlag(`${key}Required` as const, checked)}
                />
                Required
              </label>
              <label className="flex items-center gap-2 text-sm">
                <Switch
                  checked={data[`${key}CanLogin`]}
                  disabled={update.isPending}
                  onCheckedChange={(checked) => setFlag(`${key}CanLogin` as const, checked)}
                />
                Can log in
              </label>
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Auth: password policy
// ---------------------------------------------------------------------------

function AuthConfigCard({ platformSlug, organisationId }: OrgSettingsTabProps) {
  const config = useOrgAuthConfig(organisationId);
  const update = useUpdateOrgAuthConfig(platformSlug, organisationId);

  const form = useForm(authConfigSchema, {
    authType: "PASSWORD",
    passwordEnabled: true,
    passwordMinLength: 8,
    passwordMaxLength: 72,
    passwordExpirationDays: 0,
    passwordHistoryCount: 0,
  } satisfies z.input<typeof authConfigSchema>);

  useEffect(() => {
    if (config.data) {
      form.setValues({
        authType: config.data.authType,
        passwordEnabled: config.data.passwordEnabled,
        passwordMinLength: config.data.passwordMinLength,
        passwordMaxLength: config.data.passwordMaxLength,
        passwordExpirationDays: config.data.passwordExpirationDays,
        passwordHistoryCount: config.data.passwordHistoryCount,
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [config.data]);

  if (config.isLoading) return <TableSkeleton rows={4} columns={2} />;
  if (config.isError) return <ErrorState error={config.error} onRetry={() => config.refetch()} />;

  const submit = async () => {
    const data = form.values;
    await update.mutateAsync({
      authType: data.authType,
      passwordEnabled: data.passwordEnabled,
      passwordMinLength: data.passwordMinLength,
      passwordMaxLength: data.passwordMaxLength,
      passwordExpirationDays: data.passwordExpirationDays,
      passwordHistoryCount: data.passwordHistoryCount,
    });
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <KeyRound className="h-4 w-4 text-primary" /> Password policy
        </CardTitle>
        <CardDescription>
          Rules every new or reset password in this organisation must satisfy.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={form.handleSubmit(() => submit())} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <FormField
              label="Minimum length"
              htmlFor="ac-min"
              error={form.errors.passwordMinLength}
            >
              <Input
                id="ac-min"
                type="number"
                min={1}
                max={72}
                value={form.values.passwordMinLength}
                onChange={(e) => form.setValue("passwordMinLength", Number(e.target.value))}
              />
            </FormField>
            <FormField
              label="Maximum length"
              htmlFor="ac-max"
              error={form.errors.passwordMaxLength}
            >
              <Input
                id="ac-max"
                type="number"
                min={1}
                max={72}
                value={form.values.passwordMaxLength}
                onChange={(e) => form.setValue("passwordMaxLength", Number(e.target.value))}
              />
            </FormField>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <FormField
              label="Expire after (days)"
              htmlFor="ac-expiry"
              error={form.errors.passwordExpirationDays}
              hint="0 = passwords never expire"
            >
              <Input
                id="ac-expiry"
                type="number"
                min={0}
                max={3650}
                value={form.values.passwordExpirationDays}
                onChange={(e) => form.setValue("passwordExpirationDays", Number(e.target.value))}
              />
            </FormField>
            <FormField
              label="Reuse history (count)"
              htmlFor="ac-history"
              error={form.errors.passwordHistoryCount}
              hint="0 = reuse allowed"
            >
              <Input
                id="ac-history"
                type="number"
                min={0}
                max={50}
                value={form.values.passwordHistoryCount}
                onChange={(e) => form.setValue("passwordHistoryCount", Number(e.target.value))}
              />
            </FormField>
          </div>
          {form.submitError ? (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {form.submitError}
            </p>
          ) : null}
          <div className="flex justify-end">
            <Button type="submit" disabled={update.isPending} className="gap-2">
              {update.isPending ? <Loader2 className="animate-spin" /> : null}
              Save policy
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Session: token lifetimes
// ---------------------------------------------------------------------------

function SessionSettingsCard({ platformSlug, organisationId }: OrgSettingsTabProps) {
  const settings = useOrgSessionSettings(organisationId);
  const update = useUpdateOrgSessionSettings(platformSlug, organisationId);

  const [maxSessions, setMaxSessions] = useState(5);
  const [access, setAccess] = useState({ value: 15, unit: "minutes" as DurationUnit });
  const [refresh, setRefresh] = useState({ value: 7, unit: "days" as DurationUnit });
  const [errors, setErrors] = useState<{ access?: string; refresh?: string; sessions?: string }>({});
  const [submitError, setSubmitError] = useState<string | null>(null);

  // Hydrate the pickers from the fetched settings once they arrive. This
  // intentionally syncs external (query) data into local editor state.
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (settings.data) {
      setMaxSessions(settings.data.maxSessionsPerUser);
      setAccess(decomposeDuration(settings.data.accessTokenTtlSeconds));
      setRefresh(decomposeDuration(settings.data.refreshTokenTtlSeconds));
      setErrors({});
      setSubmitError(null);
    }
  }, [settings.data]);
  /* eslint-enable react-hooks/set-state-in-effect */

  if (settings.isLoading) return <TableSkeleton rows={4} columns={2} />;
  if (settings.isError) return <ErrorState error={settings.error} onRetry={() => settings.refetch()} />;

  const submit = async () => {
    const payload = {
      accessTokenTtlSeconds: toSeconds(access.value, access.unit),
      refreshTokenTtlSeconds: toSeconds(refresh.value, refresh.unit),
      maxSessionsPerUser: maxSessions,
    };
    if (access.value < 1) {
      setErrors({ access: "Value must be at least 1" });
      return;
    }
    if (refresh.value < 1) {
      setErrors({ refresh: "Value must be at least 1" });
      return;
    }
    const parsed = sessionSettingsSchema.safeParse(payload);
    if (!parsed.success) {
      const next: { access?: string; refresh?: string } = {};
      for (const issue of parsed.error.issues) {
        const key = issue.path[0];
        if (key === "accessTokenTtlSeconds") {
          next.access = issue.message;
        } else if (key === "refreshTokenTtlSeconds") {
          next.refresh = issue.message;
        }
      }
      setErrors(next);
      return;
    }
    setErrors({});
    setSubmitError(null);
    await update.mutateAsync(payload);
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Timer className="h-4 w-4 text-primary" /> Session settings
        </CardTitle>
        <CardDescription>
          Token lifetimes and concurrent sessions for this organisation&apos;s users.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void submit();
          }}
          className="space-y-4"
        >
          <DurationInput
            label="Access token lifetime"
            htmlFor="ss-access"
            value={access.value}
            unit={access.unit}
            onValueChange={(value) => setAccess((p) => ({ ...p, value }))}
            onUnitChange={(unit) => setAccess((p) => ({ ...p, unit }))}
            error={errors.access}
            hint={`${formatDuration(toSeconds(access.value, access.unit))} (max 24 hrs)`}
          />
          <DurationInput
            label="Refresh token lifetime"
            htmlFor="ss-refresh"
            value={refresh.value}
            unit={refresh.unit}
            onValueChange={(value) => setRefresh((p) => ({ ...p, value }))}
            onUnitChange={(unit) => setRefresh((p) => ({ ...p, unit }))}
            error={errors.refresh}
            hint={`${formatDuration(toSeconds(refresh.value, refresh.unit))} (max 365 days)`}
          />
          <FormField
            label="Max sessions per user"
            htmlFor="ss-sessions"
            error={errors.sessions}
            hint="Oldest sessions are evicted when the limit is reached"
          >
            <Input
              id="ss-sessions"
              type="number"
              min={1}
              max={100}
              value={maxSessions}
              onChange={(e) => setMaxSessions(Number(e.target.value))}
            />
          </FormField>
          {submitError ? (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {submitError}
            </p>
          ) : null}
          <div className="flex justify-end">
            <Button type="submit" disabled={update.isPending} className="gap-2">
              {update.isPending ? <Loader2 className="animate-spin" /> : null}
              Save settings
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Session: duration value + unit picker
// ---------------------------------------------------------------------------

interface DurationInputProps {
  label: string;
  htmlFor: string;
  value: number;
  unit: DurationUnit;
  onValueChange: (value: number) => void;
  onUnitChange: (unit: DurationUnit) => void;
  error?: string;
  hint?: string;
}

function DurationInput({
  label,
  htmlFor,
  value,
  unit,
  onValueChange,
  onUnitChange,
  error,
  hint,
}: DurationInputProps) {
  return (
    <FormField label={label} htmlFor={htmlFor} error={error} hint={hint}>
      <div className="flex items-center gap-2">
        <Input
          id={htmlFor}
          type="number"
          min={1}
          value={value}
          onChange={(e) => onValueChange(Number(e.target.value))}
          className="w-24"
        />
        <Select value={unit} onValueChange={(v) => onUnitChange(v as DurationUnit)}>
          <SelectTrigger aria-label={`${label} unit`}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {DURATION_UNITS.map((u) => (
              <SelectItem key={u.value} value={u.value}>
                {u.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
    </FormField>
  );
}

// ---------------------------------------------------------------------------
// Miscellaneous: danger zone
// ---------------------------------------------------------------------------

function DangerZoneCard({ platformSlug, organisationId }: OrgSettingsTabProps) {
  const org = useOrganisation(organisationId);
  const router = useRouter();
  const remove = useDeleteOrganisation(platformSlug, organisationId);

  const [open, setOpen] = useState(false);
  const [typedName, setTypedName] = useState("");

  if (org.isLoading) return <TableSkeleton rows={2} columns={2} />;
  if (org.isError || !org.data) return <ErrorState error={org.error ?? new Error("Organisation not found")} onRetry={() => org.refetch()} />;

  const data = org.data;

  return (
    <>
      <Card className="border-destructive/40">
        <CardHeader>
          <CardTitle className="text-base text-destructive">Danger zone</CardTitle>
          <CardDescription>
            Deleting an organisation removes its users, roles, signing keys and sessions permanently.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button
            variant="destructive"
            className="gap-2"
            onClick={() => {
              setTypedName("");
              setOpen(true);
            }}
          >
            <Trash2 /> Delete organisation
          </Button>
        </CardContent>
      </Card>

      {/* Delete confirm — the organisation name must be typed exactly. */}
      <Dialog open={open} onOpenChange={(next) => !remove.isPending && setOpen(next)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Delete this organisation?</DialogTitle>
            <DialogDescription>
              This permanently removes the organisation and all of its users, roles,
              signing keys and sessions. It cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (typedName.trim() !== data.name || remove.isPending) return;
              void (async () => {
                await remove.mutateAsync();
                setOpen(false);
                router.replace("/console/organisations");
              })();
            }}
            className="space-y-4"
          >
            <div className="rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2 font-mono text-sm">
              {data.name}
            </div>
            <FormField label="Type the organisation name to confirm" htmlFor="delete-confirm">
              <Input
                id="delete-confirm"
                value={typedName}
                onChange={(e) => setTypedName(e.target.value)}
                autoFocus
                autoComplete="off"
                aria-invalid={typedName.trim() !== "" && typedName.trim() !== data.name}
              />
            </FormField>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setOpen(false)}
                disabled={remove.isPending}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                variant="destructive"
                className="gap-2"
                disabled={remove.isPending || typedName.trim() !== data.name}
              >
                {remove.isPending ? <Loader2 className="animate-spin" /> : <Trash2 />}
                Delete organisation
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
