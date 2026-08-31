"use client";

import { Loader2, Link } from "lucide-react";
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
import { useOrgRoles } from "@/hooks/queries";
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
  const roles = useOrgRoles(organisationId);
  const pending = update.isPending;

  const [enabled, setEnabled] = useState(client.enabled);
  const [allowRegister, setAllowRegister] = useState(client.allowRegister);
  const [allowLogin, setAllowLogin] = useState(client.allowLogin);
  const [useRoleRestrictions, setUseRoleRestrictions] = useState(
    !!(client.allowedRoles && client.allowedRoles.length > 0),
  );
  const [selectedRoles, setSelectedRoles] = useState<Set<string>>(
    new Set(client.allowedRoles ?? []),
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

  const toggleRole = (roleName: string) => {
    setSelectedRoles((prev) => {
      const next = new Set(prev);
      if (next.has(roleName)) {
        next.delete(roleName);
      } else {
        next.add(roleName);
      }
      return next;
    });
  };

  const submit = async () => {
    const sessionPayload = useSessionOverrides
      ? { accessTokenTtlSeconds: accessTokenTtl, refreshTokenTtlSeconds: refreshTokenTtl, maxSessionsPerUser: maxSessions }
      : { accessTokenTtlSeconds: -1, refreshTokenTtlSeconds: -1, maxSessionsPerUser: -1 };

    const allowedRoles = useRoleRestrictions && selectedRoles.size > 0
      ? Array.from(selectedRoles)
      : [];

    await update.mutateAsync({
      clientKey: client.clientKey,
      body: {
        name: client.name,
        enabled,
        ...sessionPayload,
        allowRegister,
        allowLogin,
        allowedRoles,
      },
    });
    onOpenChange(false);
  };

  const availableRoles = roles.data ?? [];

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
          {/* Links summary */}
          {(client.links ?? []).length > 0 && (
            <div className="rounded-lg border p-3">
              <div className="flex items-center gap-2">
                <Link className="h-4 w-4 text-muted-foreground" />
                <p className="text-sm font-medium">
                  {(client.links ?? []).length} link{(client.links ?? []).length === 1 ? "" : "s"} configured
                </p>
              </div>
              {(client.links ?? []).some((l) => l.limitSource) && (
                <p className="mt-1 text-xs text-amber-600 dark:text-amber-400">
                  {(client.links ?? []).filter((l) => l.limitSource).length} with source restriction — only requests from those origins are allowed.
                </p>
              )}
            </div>
          )}

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

            {/* Role restrictions */}
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
              <div className="space-y-2">
                {availableRoles.length === 0 ? (
                  <p className="text-xs text-muted-foreground italic">
                    No roles created yet. Create roles first to restrict access.
                  </p>
                ) : (
                  <div className="rounded-md border divide-y">
                    {availableRoles.map((role) => (
                      <label
                        key={role.id}
                        className="flex items-center justify-between gap-3 px-3 py-2 cursor-pointer hover:bg-muted/50 transition-colors"
                      >
                        <div className="min-w-0">
                          <span className="text-sm font-medium">{role.name}</span>
                          {role.isDefault && (
                            <span className="ml-2 text-[10px] text-muted-foreground">(default)</span>
                          )}
                          {role.permissions.length > 0 && (
                            <p className="text-[11px] text-muted-foreground truncate">
                              {role.permissions.length} permission{role.permissions.length !== 1 ? "s" : ""}
                            </p>
                          )}
                        </div>
                        <input
                          type="checkbox"
                          checked={selectedRoles.has(role.name)}
                          onChange={() => toggleRole(role.name)}
                          className="h-4 w-4 rounded border-input accent-primary shrink-0"
                        />
                      </label>
                    ))}
                  </div>
                )}
                {selectedRoles.size > 0 && (
                  <p className="text-[11px] text-muted-foreground">
                    {selectedRoles.size} role{selectedRoles.size !== 1 ? "s" : ""} allowed
                  </p>
                )}
              </div>
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
