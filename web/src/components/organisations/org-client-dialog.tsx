"use client";

import { useState } from "react";
import { Loader2, ShieldCheck } from "lucide-react";
import { FormField } from "@/components/shared/form-field";
import { ToneBadge } from "@/components/shared/status-badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { useCreateOrgClient, useUpdateOrgClient } from "@/hooks/mutations";
import { useForm } from "@/hooks/use-form";
import { DEFAULT_SESSION_SETTINGS, formatDuration } from "@/lib/constants";
import { orgClientFormSchema } from "@/lib/validation";
import type { OrganisationClientResponse } from "@/types/api";
import { CLIENT_TYPE_AUTH_MODE, CLIENT_TYPE_META, CLIENT_TYPE_TONE, type ClientType } from "@/types/enums";

interface OrgClientDialogProps {
  platformSlug: string;
  organisationId: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  client?: OrganisationClientResponse;
  /** Called with the created client so the caller can surface its one-time token. */
  onCreated?: (client: OrganisationClientResponse) => void;
}

const CLIENT_TYPES: ClientType[] = ["WEB", "ANDROID", "IOS", "SERVER"];

const AUTH_HINTS: Record<ClientType, string> = {
  WEB: "Web clients never authenticate — they sign users in via the login endpoint.",
  ANDROID: "When on, the client authenticates with a static token; when off it can only reach login and register.",
  IOS: "When on, the client authenticates with a static token; when off it can only reach login and register.",
  SERVER: "Server clients always authenticate with a static token.",
};

export function OrgClientDialog({
  platformSlug,
  organisationId,
  open,
  onOpenChange,
  client,
  onCreated,
}: OrgClientDialogProps) {
  const isEdit = !!client;
  const create = useCreateOrgClient(platformSlug, organisationId);
  const update = useUpdateOrgClient(platformSlug, organisationId);
  const pending = create.isPending || update.isPending;

  const [useRoleRestrictions, setUseRoleRestrictions] = useState(
    !!(client?.allowedRoles && client.allowedRoles.length > 0),
  );
  const [allowedRolesInput, setAllowedRolesInput] = useState(
    client?.allowedRoles?.join(", ") ?? "",
  );

  const [useSessionOverrides, setUseSessionOverrides] = useState(
    !!(client?.accessTokenTtlSeconds || client?.refreshTokenTtlSeconds || client?.maxSessionsPerUser),
  );
  const [accessTokenTtl, setAccessTokenTtl] = useState(client?.accessTokenTtlSeconds ?? DEFAULT_SESSION_SETTINGS.accessTokenTtlSeconds);
  const [refreshTokenTtl, setRefreshTokenTtl] = useState(client?.refreshTokenTtlSeconds ?? DEFAULT_SESSION_SETTINGS.refreshTokenTtlSeconds);
  const [maxSessions, setMaxSessions] = useState(client?.maxSessionsPerUser ?? DEFAULT_SESSION_SETTINGS.maxSessionsPerUser);

  const form = useForm(orgClientFormSchema, {
    name: client?.name ?? "",
    type: client?.type ?? "WEB",
    requireAuthentication: client?.requireAuthentication ?? false,
    allowedOrigins: client?.allowedOrigins.join("\n") ?? "",
    enabled: client?.enabled ?? true,
    allowRegister: client?.allowRegister ?? true,
    allowLogin: client?.allowLogin ?? true,
  });

  // NOTE: this component must be keyed by the client (see OrgClientsTab).
  // useForm and every useState above only read their initial values on mount,
  // so a remount per opened client is what keeps edit mode showing the
  // existing data instead of stale/empty fields.

  const authMode = CLIENT_TYPE_AUTH_MODE[form.values.type];
  const authLocked = authMode !== "optional";

  const selectType = (type: ClientType) => {
    form.setValue("type", type);
    form.setValue("requireAuthentication", CLIENT_TYPE_AUTH_MODE[type] === "auth");
  };

  const submit = async () => {
    const data = form.values;
    const allowedOrigins = data.allowedOrigins
      .split("\n")
      .map((origin) => origin.trim())
      .filter(Boolean);
    const authOptional = CLIENT_TYPE_AUTH_MODE[data.type] === "optional";
    const sessionPayload = useSessionOverrides
      ? { accessTokenTtlSeconds: accessTokenTtl, refreshTokenTtlSeconds: refreshTokenTtl, maxSessionsPerUser: maxSessions }
      : {};

    // Parse allowed roles
    const allowedRoles = useRoleRestrictions && allowedRolesInput.trim()
      ? new Set(allowedRolesInput.split(",").map(r => r.trim()).filter(Boolean))
      : null; // null = clear restrictions

    if (isEdit && client) {
      await update.mutateAsync({
        clientKey: client.clientKey,
        body: {
          name: data.name,
          enabled: data.enabled,
          allowedOrigins,
          ...(authOptional ? { requireAuthentication: data.requireAuthentication } : {}),
          // When overrides disabled, send -1 to clear back to org defaults.
          ...(useSessionOverrides ? sessionPayload : { accessTokenTtlSeconds: -1, refreshTokenTtlSeconds: -1, maxSessionsPerUser: -1 }),
          allowRegister: data.allowRegister,
          allowLogin: data.allowLogin,
          allowedRoles: useRoleRestrictions ? allowedRoles : new Set(),
        },
      });
      onOpenChange(false);
    } else {
      const created = await create.mutateAsync({
        name: data.name,
        type: data.type,
        enabled: data.enabled,
        allowedOrigins,
        ...(authOptional ? { requireAuthentication: data.requireAuthentication } : {}),
        ...(useSessionOverrides ? sessionPayload : {}),
        allowRegister: data.allowRegister,
        allowLogin: data.allowLogin,
        allowedRoles: useRoleRestrictions ? allowedRoles : undefined,
      });
      onCreated?.(created);
      onOpenChange(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !pending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{isEdit ? "Edit client" : "New client"}</DialogTitle>
          <DialogDescription>
            A client is an external app that talks to this organisation&apos;s API. The type is fixed
            at creation and drives how it authenticates.
          </DialogDescription>
        </DialogHeader>
        <form id="org-client-form" onSubmit={form.handleSubmit(() => submit())} className="space-y-4">
          <FormField label="Name" htmlFor="client-name" error={form.errors.name}>
            <Input
              id="client-name"
              placeholder="e.g. Storefront web"
              value={form.values.name}
              onChange={(e) => form.setValue("name", e.target.value)}
            />
          </FormField>

          {isEdit && client ? (
            <FormField label="Type" hint={CLIENT_TYPE_META[client.type].description}>
              <div className="pt-1">
                <ToneBadge tone={CLIENT_TYPE_TONE[client.type]}>{CLIENT_TYPE_META[client.type].label}</ToneBadge>
              </div>
            </FormField>
          ) : (
            <FormField
              label="Type"
              htmlFor="client-type"
              hint={CLIENT_TYPE_META[form.values.type].description}
              error={form.errors.type}
            >
              <Select value={form.values.type} onValueChange={(type) => selectType(type as ClientType)}>
                <SelectTrigger id="client-type" className="w-full">
                  <SelectValue placeholder="Select a type" />
                </SelectTrigger>
                <SelectContent>
                  {CLIENT_TYPES.map((type) => (
                    <SelectItem key={type} value={type}>
                      {CLIENT_TYPE_META[type].label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </FormField>
          )}

          <FormField label="Token authentication" hint={AUTH_HINTS[form.values.type]}>
            <div className="flex items-center justify-between rounded-md border px-3 py-2">
              <span className="text-sm">
                {form.values.requireAuthentication ? "Authenticated client" : "Sign-in only"}
              </span>
              <Switch
                checked={form.values.requireAuthentication}
                disabled={authLocked}
                onCheckedChange={(checked) => form.setValue("requireAuthentication", checked)}
              />
            </div>
          </FormField>

          <FormField
            label="Allowed origins"
            htmlFor="client-origins"
            hint="Browser CORS allow-list, one origin per line (https://app.example.com). Leave empty to allow none."
            error={form.errors.allowedOrigins}
          >
            <Textarea
              id="client-origins"
              placeholder={"https://app.example.com\nhttps://dashboard.example.com"}
              value={form.values.allowedOrigins}
              onChange={(e) => form.setValue("allowedOrigins", e.target.value)}
            />
          </FormField>

          <FormField label="Enabled" hint="A disabled client is rejected with 401 on every request.">
            <div className="flex items-center justify-between rounded-md border px-3 py-2">
              <span className="text-sm">{form.values.enabled ? "Enabled" : "Disabled"}</span>
              <Switch
                checked={form.values.enabled}
                onCheckedChange={(checked) => form.setValue("enabled", checked)}
              />
            </div>
          </FormField>

          <div className="rounded-lg border p-3 space-y-3">
            <div className="flex items-center justify-between gap-4">
              <div>
                <p className="text-sm font-medium">Session overrides</p>
                <p className="text-xs text-muted-foreground">
                  Override the organisation&apos;s default session settings for this client.
                </p>
              </div>
              <Switch
                checked={useSessionOverrides}
                onCheckedChange={setUseSessionOverrides}
              />
            </div>
            {useSessionOverrides ? (
              <div className="grid gap-3 sm:grid-cols-3">
                <FormField label="Access token TTL" hint={`Org default: ${formatDuration(DEFAULT_SESSION_SETTINGS.accessTokenTtlSeconds)}`}>
                  <Input
                    type="number"
                    min={60}
                    max={86400}
                    value={accessTokenTtl}
                    onChange={(e) => setAccessTokenTtl(Number(e.target.value))}
                  />
                </FormField>
                <FormField label="Refresh token TTL" hint={`Org default: ${formatDuration(DEFAULT_SESSION_SETTINGS.refreshTokenTtlSeconds)}`}>
                  <Input
                    type="number"
                    min={300}
                    max={31536000}
                    value={refreshTokenTtl}
                    onChange={(e) => setRefreshTokenTtl(Number(e.target.value))}
                  />
                </FormField>
                <FormField label="Max sessions" hint={`Org default: ${DEFAULT_SESSION_SETTINGS.maxSessionsPerUser}`}>
                  <Input
                    type="number"
                    min={1}
                    max={100}
                    value={maxSessions}
                    onChange={(e) => setMaxSessions(Number(e.target.value))}
                  />
                </FormField>
              </div>
            ) : null}
          </div>

          <div className="rounded-lg border p-3 space-y-3">
            <div className="flex items-center justify-between gap-4">
              <div>
                <p className="text-sm font-medium">Access restrictions</p>
                <p className="text-xs text-muted-foreground">
                  Control what this client can do and which roles may use it.
                </p>
              </div>
            </div>
            <div className="space-y-2">
              <FormField label="Allow registration" hint="When off, users cannot register via this client.">
                <div className="flex items-center justify-between rounded-md border px-3 py-2">
                  <span className="text-sm">{form.values.allowRegister ? "Allowed" : "Blocked"}</span>
                  <Switch
                    checked={form.values.allowRegister}
                    onCheckedChange={(checked) => form.setValue("allowRegister", checked)}
                  />
                </div>
              </FormField>
              <FormField label="Allow login" hint="When off, users cannot login via this client.">
                <div className="flex items-center justify-between rounded-md border px-3 py-2">
                  <span className="text-sm">{form.values.allowLogin ? "Allowed" : "Blocked"}</span>
                  <Switch
                    checked={form.values.allowLogin}
                    onCheckedChange={(checked) => form.setValue("allowLogin", checked)}
                  />
                </div>
              </FormField>
              <div className="flex items-center justify-between gap-4">
                <div>
                  <p className="text-sm font-medium">Role restrictions</p>
                  <p className="text-xs text-muted-foreground">
                    Only allow specific roles to authenticate via this client.
                  </p>
                </div>
                <Switch
                  checked={useRoleRestrictions}
                  onCheckedChange={setUseRoleRestrictions}
                />
              </div>
              {useRoleRestrictions ? (
                <FormField
                  label="Allowed roles"
                  hint="Comma-separated role names. Users without one of these roles will be rejected."
                >
                  <Input
                    placeholder="e.g. ADMIN, SUPPORT, USER"
                    value={allowedRolesInput}
                    onChange={(e) => setAllowedRolesInput(e.target.value)}
                  />
                </FormField>
              ) : null}
            </div>
          </div>

          {form.submitError ? (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {form.submitError}
            </p>
          ) : null}
        </form>
        <DialogFooter>
          <Button type="submit" form="org-client-form" disabled={pending} className="gap-2">
            {pending ? <Loader2 className="animate-spin" /> : null}
            {isEdit ? "Save changes" : "Create client"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
