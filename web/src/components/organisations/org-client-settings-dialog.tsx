"use client";

import { Loader2 } from "lucide-react";
import { useState } from "react";
import { FormField } from "@/components/shared/form-field";
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
import { Switch } from "@/components/ui/switch";
import { useUpdateOrgClient } from "@/hooks/mutations";
import { DEFAULT_SESSION_SETTINGS, formatDuration } from "@/lib/constants";
import type { OrganisationClientResponse } from "@/types/api";

interface OrgClientSettingsDialogProps {
  platformSlug: string;
  organisationId: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  client: OrganisationClientResponse;
}

export function OrgClientSettingsDialog({
  platformSlug,
  organisationId,
  open,
  onOpenChange,
  client,
}: OrgClientSettingsDialogProps) {
  const update = useUpdateOrgClient(platformSlug, organisationId);
  const pending = update.isPending;

  const [enabled, setEnabled] = useState(client.enabled);
  const [allowRegister, setAllowRegister] = useState(client.allowRegister);
  const [allowLogin, setAllowLogin] = useState(client.allowLogin);
  const [useRoleRestrictions, setUseRoleRestrictions] = useState(
    !!(client.allowedRoles && client.allowedRoles.length > 0),
  );
  const [allowedRolesInput, setAllowedRolesInput] = useState(
    client.allowedRoles?.join(", ") ?? "",
  );
  const [useSessionOverrides, setUseSessionOverrides] = useState(
    !!(client.accessTokenTtlSeconds || client.refreshTokenTtlSeconds || client.maxSessionsPerUser),
  );
  const [accessTokenTtl, setAccessTokenTtl] = useState(
    client.accessTokenTtlSeconds ?? DEFAULT_SESSION_SETTINGS.accessTokenTtlSeconds,
  );
  const [refreshTokenTtl, setRefreshTokenTtl] = useState(
    client.refreshTokenTtlSeconds ?? DEFAULT_SESSION_SETTINGS.refreshTokenTtlSeconds,
  );
  const [maxSessions, setMaxSessions] = useState(
    client.maxSessionsPerUser ?? DEFAULT_SESSION_SETTINGS.maxSessionsPerUser,
  );

  const submit = async () => {
    const sessionPayload = useSessionOverrides
      ? { accessTokenTtlSeconds: accessTokenTtl, refreshTokenTtlSeconds: refreshTokenTtl, maxSessionsPerUser: maxSessions }
      : { accessTokenTtlSeconds: -1, refreshTokenTtlSeconds: -1, maxSessionsPerUser: -1 };

    const allowedRoles = useRoleRestrictions && allowedRolesInput.trim()
      ? new Set(allowedRolesInput.split(",").map(r => r.trim()).filter(Boolean))
      : new Set<string>();

    await update.mutateAsync({
      clientKey: client.clientKey,
      body: {
        name: client.name,
        enabled,
        allowedOrigins: client.allowedOrigins,
        ...sessionPayload,
        allowRegister,
        allowLogin,
        allowedRoles,
      },
    });
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !pending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Client settings</DialogTitle>
          <DialogDescription>
            Configure access, sessions and restrictions for <strong>{client.name}</strong>.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Enabled */}
          <FormField label="Enabled" hint="A disabled client is rejected with 401 on every request.">
            <div className="flex items-center justify-between rounded-md border px-3 py-2">
              <span className="text-sm">{enabled ? "Enabled" : "Disabled"}</span>
              <Switch checked={enabled} onCheckedChange={setEnabled} />
            </div>
          </FormField>

          {/* Access restrictions */}
          <div className="rounded-lg border p-3 space-y-3">
            <p className="text-sm font-medium">Access restrictions</p>
            <FormField label="Allow registration" hint="When off, users cannot register via this client.">
              <div className="flex items-center justify-between rounded-md border px-3 py-2">
                <span className="text-sm">{allowRegister ? "Allowed" : "Blocked"}</span>
                <Switch checked={allowRegister} onCheckedChange={setAllowRegister} />
              </div>
            </FormField>
            <FormField label="Allow login" hint="When off, users cannot login via this client.">
              <div className="flex items-center justify-between rounded-md border px-3 py-2">
                <span className="text-sm">{allowLogin ? "Allowed" : "Blocked"}</span>
                <Switch checked={allowLogin} onCheckedChange={setAllowLogin} />
              </div>
            </FormField>
            <div className="flex items-center justify-between gap-4">
              <div>
                <p className="text-sm font-medium">Role restrictions</p>
                <p className="text-xs text-muted-foreground">
                  Only allow specific roles to authenticate via this client.
                </p>
              </div>
              <Switch checked={useRoleRestrictions} onCheckedChange={setUseRoleRestrictions} />
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

          {/* Session overrides */}
          <div className="rounded-lg border p-3 space-y-3">
            <div className="flex items-center justify-between gap-4">
              <div>
                <p className="text-sm font-medium">Session overrides</p>
                <p className="text-xs text-muted-foreground">
                  Override the organisation&apos;s default session settings for this client.
                </p>
              </div>
              <Switch checked={useSessionOverrides} onCheckedChange={setUseSessionOverrides} />
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

          {update.isError ? (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {update.error?.message ?? "Something went wrong"}
            </p>
          ) : null}
        </div>

        <DialogFooter>
          <Button onClick={submit} disabled={pending} className="gap-2">
            {pending ? <Loader2 className="animate-spin" /> : null}
            Save settings
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
